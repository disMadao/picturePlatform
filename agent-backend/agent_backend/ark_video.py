"""
方舟（Seedance）视频生成：创建任务、轮询、解析临时视频 URL。
依赖：pip install 'volcengine-python-sdk[ark]'
"""

from __future__ import annotations

import logging
import os
import re
import time
from io import BytesIO
from typing import Any, Dict, Optional, Tuple

import httpx
from PIL import Image

from .config import config

# 方舟 Seedance 对参考图有最小边限制（报错示例：height to be at least 300px）
ARK_REF_IMAGE_MIN_HEIGHT = 300

logger = logging.getLogger(__name__)

_VIDEO_URL_RE = re.compile(
    r"https?://[^\s\"'<>]+\.(?:mp4|webm|mov)(?:\?[^\s\"'<>]*)?",
    re.IGNORECASE,
)


def _get_api_key() -> Optional[str]:
    return os.getenv("ARK_API_KEY") or os.getenv("SEEDANCE_API_KEY")


def _extract_video_url(obj: Any) -> Optional[str]:
    """从任务查询结果中尽量稳健地取出视频直链。"""
    if obj is None:
        return None
    if isinstance(obj, str):
        m = _VIDEO_URL_RE.search(obj)
        return m.group(0) if m else None
    if isinstance(obj, dict):
        for k in ("url", "video_url", "videoUrl", "output_url", "download_url"):
            v = obj.get(k)
            if isinstance(v, str) and v.startswith("http"):
                return v
            if isinstance(v, dict):
                u = v.get("url")
                if isinstance(u, str) and u.startswith("http"):
                    return u
        for v in obj.values():
            u = _extract_video_url(v)
            if u:
                return u
    if isinstance(obj, (list, tuple)):
        for item in obj:
            u = _extract_video_url(item)
            if u:
                return u
    for attr in ("content", "output", "result", "data"):
        if hasattr(obj, attr):
            u = _extract_video_url(getattr(obj, attr))
            if u:
                return u
    try:
        if hasattr(obj, "model_dump"):
            return _extract_video_url(obj.model_dump())
    except Exception:
        pass
    if hasattr(obj, "__dict__"):
        return _extract_video_url(vars(obj))
    return _VIDEO_URL_RE.search(str(obj)).group(0) if _VIDEO_URL_RE.search(str(obj)) else None


def _probe_image_wh(url: str) -> Tuple[int, int]:
    """下载图片并读取宽高（像素）。"""
    with httpx.Client(timeout=45.0, follow_redirects=True) as client:
        r = client.get(url.strip())
        r.raise_for_status()
        data = r.content
    with Image.open(BytesIO(data)) as im:
        w, h = im.size
    return int(w), int(h)


def validate_ark_reference_image_urls(
    first_frame_url: Optional[str],
    last_frame_url: Optional[str],
    *,
    min_height: int = ARK_REF_IMAGE_MIN_HEIGHT,
) -> None:
    """
    在调用方舟前校验首帧/尾帧图尺寸，避免 400 InvalidParameter（图高不足等）。
    """
    pairs: list[tuple[str, str]] = []
    if first_frame_url and first_frame_url.strip():
        pairs.append(("首帧", first_frame_url.strip()))
    if last_frame_url and last_frame_url.strip():
        u = last_frame_url.strip()
        if u != (first_frame_url or "").strip():
            pairs.append(("尾帧", u))

    for label, u in pairs:
        try:
            w, h = _probe_image_wh(u)
        except Exception as e:
            raise RuntimeError(
                f"{label}图无法下载或解析（请检查 URL 是否公网可访问、且为常见图片格式）：{e}"
            ) from e
        if h < min_height:
            raise RuntimeError(
                f"{label}图不符合方舟要求：当前尺寸 {w}×{h}px，"
                f"高度需≥{min_height}px（官方会校验下载后的像素）。请换一张更高的图或先裁切/放大后再试。"
            )


def _build_content(
    prompt: str,
    first_frame_url: Optional[str],
    last_frame_url: Optional[str],
) -> list:
    content: list = [{"type": "text", "text": prompt}]
    if first_frame_url:
        content.append(
            {"type": "image_url", "image_url": {"url": first_frame_url.strip()}}
        )
    if last_frame_url and last_frame_url.strip() != (first_frame_url or "").strip():
        content.append(
            {"type": "image_url", "image_url": {"url": last_frame_url.strip()}}
        )
    return content


def create_task_and_wait_for_video_url(
    prompt: str,
    *,
    first_frame_url: Optional[str] = None,
    last_frame_url: Optional[str] = None,
    max_wait_sec: int = 600,
) -> Tuple[str, str, Dict[str, Any]]:
    """
    创建方舟视频任务并轮询直到成功或失败。
    返回 (task_id, temp_video_url, last_get_payload_dict)
    """
    try:
        from volcenginesdkarkruntime import Ark
    except ImportError as e:
        raise RuntimeError(
            "未安装方舟 SDK，请执行: pip install 'volcengine-python-sdk[ark]'"
        ) from e

    api_key = _get_api_key()
    if not api_key:
        raise RuntimeError("未配置 ARK_API_KEY（或 SEEDANCE_API_KEY）")

    validate_ark_reference_image_urls(first_frame_url, last_frame_url)

    client = Ark(base_url=config.ark_base_url, api_key=api_key)
    model = config.ark_video_model
    content = _build_content(prompt, first_frame_url, last_frame_url)

    create_result = client.content_generation.tasks.create(model=model, content=content)
    task_id = getattr(create_result, "id", None) or str(create_result)
    if not task_id:
        raise RuntimeError("方舟创建任务未返回 task id")

    deadline = time.monotonic() + max_wait_sec
    last_payload: Dict[str, Any] = {}
    while time.monotonic() < deadline:
        get_result = client.content_generation.tasks.get(task_id=task_id)
        status = getattr(get_result, "status", None) or ""
        last_payload = {"status": status}
        try:
            if hasattr(get_result, "model_dump"):
                last_payload["raw"] = get_result.model_dump()
            elif hasattr(get_result, "dict"):
                last_payload["raw"] = get_result.dict()
        except Exception:
            last_payload["raw"] = str(get_result)

        if status == "succeeded":
            url = _extract_video_url(get_result)
            if not url:
                logger.error("任务成功但未解析到视频 URL: %s", get_result)
                raise RuntimeError("方舟任务成功但未解析到视频下载地址，请查看日志中的原始响应")
            return str(task_id), url, last_payload
        if status == "failed":
            err = getattr(get_result, "error", None)
            raise RuntimeError(f"方舟视频任务失败: {err or get_result}")

        time.sleep(config.ark_poll_interval_sec)

    raise RuntimeError(f"方舟视频任务超时（>{max_wait_sec}s），task_id={task_id}")
