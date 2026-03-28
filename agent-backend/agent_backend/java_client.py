from typing import Any, Dict, List, Optional

import httpx
import os

from .config import config


class JavaBackendClient:
    """
    Java 图片后端 HTTP 客户端封装。
    作为 Agent 的一个“工具”，用于通过原有后端按条件查询图片。
    """

    def __init__(self, base_url: str | None = None) -> None:
        self.base_url = base_url or config.java_base_url
        # 可选：Java 后端内部调用鉴权（推荐），避免在 Agent 中保存账号密码
        internal_token = os.getenv("AGENT_INTERNAL_TOKEN")
        # 复用 cookies（Java 后端登录态依赖 HttpSession + Sa-Token 的 cookie）
        headers = {}
        if internal_token:
            headers["X-Internal-Token"] = internal_token
        self._client = httpx.Client(timeout=10.0, follow_redirects=False, headers=headers)
        self._logged_in = False
        self._has_internal_token = bool(internal_token)

    def _url(self, path: str) -> str:
        return f"{self.base_url.rstrip('/')}{path}"

    def _maybe_login(self) -> None:
        """
        替代方案：在python端和java端中间使用一个内部的特殊token进行鉴权，避免在Agent中保存账号密码


        (下面是deprecated的方案)
        Java 后端采用 HttpSession + Sa-Token：
        - /user/login 成功后会 Set-Cookie（如 JSESSIONID/SESSION + satoken）
        - 后续请求只要带 cookie 就会被认为“已登录”

        最简方案：在 Agent 中配置一个管理员账号，自动登录并缓存 cookie。

        环境变量：
        - AGENT_JAVA_USER_ACCOUNT / AGENT_JAVA_USER_PASSWORD
          （或 JAVA_USER_ACCOUNT / JAVA_USER_PASSWORD）
        """
        if self._logged_in:
            return
        # 已配置 internal token 时，不需要账号密码登录
        if self._has_internal_token:
            self._logged_in = True
            return
        # old version, use account and password login, deprecated!
        # user_account = os.getenv("AGENT_JAVA_USER_ACCOUNT") or os.getenv("JAVA_USER_ACCOUNT")
        # user_password = os.getenv("AGENT_JAVA_USER_PASSWORD") or os.getenv("JAVA_USER_PASSWORD")
        # if not user_account or not user_password:
        #     return
        # payload = {"userAccount": user_account, "userPassword": user_password}
        # resp = self._client.post(self._url("/user/login"), json=payload)
        # resp.raise_for_status()
        # data = resp.json()
        # if data.get("code") != 0:
        #     raise RuntimeError(f"Java login failed: {data.get('message')}")
        # self._logged_in = True

    def _request_json(self, method: str, path: str, **kwargs) -> dict:
        """
        统一请求封装：遇到 NOT_LOGIN（40100）时，自动登录并重试一次。
        可选 request_timeout：单次请求超时秒数（如视频转存 COS 较慢）。
        """
        req_timeout = kwargs.pop("request_timeout", None)

        def _request(**kw: Any) -> Any:
            if req_timeout is not None:
                kw["timeout"] = req_timeout
            return self._client.request(method, self._url(path), **kw)

        resp = _request(**kwargs)
        resp.raise_for_status()
        data = resp.json()
        if data.get("code") == 40100:
            # 未登录：尝试登录并重试
            self._logged_in = False
            self._maybe_login()
            resp2 = _request(**kwargs)
            resp2.raise_for_status()
            return resp2.json()
        return data

    def list_picture_vo_by_text(
        self,
        search_text: str,
        page: int = 1,
        page_size: int = 20,
    ) -> List[Dict[str, Any]]:
        """
        调用 Java 后端的 /picture/list/page/vo，按文本条件搜索图片。
        这里模拟 BaseResponse 结构，只返回 data.records 数组。
        """
        payload = {
            "current": page,
            "pageSize": page_size,
            "searchText": search_text,
        }
        data = self._request_json("POST", "/picture/list/page/vo", json=payload)
        # 兼容原有 BaseResponse 结构
        if data.get("code") != 0:
            raise RuntimeError(f"Java backend error: {data.get('message')}")
        page_data = data.get("data") or {}
        return page_data.get("records") or []

    def get_picture_vo_by_id(self, picture_id: int | str) -> Optional[Dict[str, Any]]:
        """
        调用 Java 后端的 /picture/get/vo，按 id 获取单张图片详情。
        """
        # 私有空间图片会做权限校验，因此优先尝试登录（未配置账号则会返回 40100）
        self._maybe_login()

        data = self._request_json("GET", "/picture/get/vo", params={"id": picture_id})
        print("data = ", data)
        if data.get("code") != 0:
            return None
        return data.get("data")

    def persist_agent_video(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """
        调用 Java POST /video/agent/persist，将方舟临时视频转 COS 并落库。
        """
        self._maybe_login()
        data = self._request_json(
            "POST",
            "/video/agent/persist",
            json=body,
            request_timeout=600.0,
        )
        if data.get("code") != 0:
            msg = data.get("message") or "Java persist 失败"
            if "空间不存在" in (msg or ""):
                msg = (
                    f"{msg}（常见原因：Agent 的 JAVA_BASE_URL={self.base_url!r} 与浏览器访问的 Java "
                    "不是同一套库；请把 .env 里 JAVA_BASE_URL 设为 http://127.0.0.1:8123/api 并重启 Agent。"
                )
            raise RuntimeError(msg)
        return data.get("data") or {}


java_client = JavaBackendClient()


