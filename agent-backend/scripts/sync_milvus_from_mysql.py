"""
把 MySQL 的 picture 数据同步到 Milvus（用于向量搜索）。

你需要做两件事：
1) 启动 Milvus（你已经用 docker-compose 起了）
2) 安装一个“向量编码器”
   - 推荐：sentence-transformers（需要 torch，体积较大，但最省事）
   - 或者你也可以把 embed_text() 替换成调用在线 embedding 接口

环境变量（可选）：
- MYSQL_HOST / MYSQL_PORT / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DB
- MILVUS_URI（默认 http://127.0.0.1:19530）
- MILVUS_COLLECTION（默认 picture_vectors）
- EMBEDDING_MODEL（默认 sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2，向量维度 384）
"""

from __future__ import annotations

import json
import os
from typing import Iterable, List, Optional, Tuple

import pymysql
from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility


def get_mysql_conn():
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", "root"),
        database=os.getenv("MYSQL_DB", "yu_picture"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )


def iter_pictures(conn) -> Iterable[dict]:
    """
    读取 picture 表基础字段。
    这里不需要把整个 MySQL 搬到 Milvus，只需要：
    - picture_id（主键，对应回查 Java 后端 / MySQL）
    - embedding（向量）
    可选：额外字段（如 spaceId/category）也能放 Milvus 做过滤，但第一版先不做。
    """
    sql = """
    SELECT id, name, category, introduction, tags, url
    FROM picture
    WHERE isDelete = 0
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        for row in cur.fetchall():
            yield row


def build_text(row: dict) -> str:
    """
    把一张图片的可检索文本拼起来做 embedding。
    """
    parts: List[str] = []
    for k in ("name", "category", "introduction"):
        v = (row.get(k) or "").strip()
        if v:
            parts.append(v)
    tags = row.get("tags")
    if tags:
        # tags 在 Java 后端里通常是 JSON 字符串
        try:
            tag_list = json.loads(tags)
            if isinstance(tag_list, list):
                parts.extend([str(t).strip() for t in tag_list if str(t).strip()])
        except Exception:
            parts.append(str(tags))
    return "，".join(parts)


def load_embedder():
    """
    优先用 sentence-transformers；如果你想用别的 embedding 方式，替换这个函数即可。
    """
    model_name = os.getenv(
        "EMBEDDING_MODEL", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    )
    try:
        from sentence_transformers import SentenceTransformer  # type: ignore
    except Exception as e:
        raise RuntimeError(
            "缺少依赖：sentence-transformers（以及 torch）。\n"
            "安装示例：pip install sentence-transformers\n"
            f"原始错误：{e}"
        )
    return SentenceTransformer(model_name)


def embed_text(embedder, texts: List[str]) -> List[List[float]]:
    vecs = embedder.encode(texts, normalize_embeddings=True)
    # numpy -> python list
    return [v.tolist() for v in vecs]


def ensure_collection(collection_name: str, dim: int) -> Collection:
    """
    创建 collection + 索引（若不存在）。
    """
    if utility.has_collection(collection_name):
        return Collection(collection_name)

    fields = [
        FieldSchema(name="picture_id", dtype=DataType.INT64, is_primary=True, auto_id=False),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=dim),
    ]
    schema = CollectionSchema(fields=fields, description="Picture embeddings for search")
    collection = Collection(name=collection_name, schema=schema)

    # IVF_FLAT：第一版简单稳定
    index_params = {
        "index_type": "IVF_FLAT",
        "metric_type": "COSINE",
        "params": {"nlist": 1024},
    }
    collection.create_index(field_name="embedding", index_params=index_params)
    collection.load()
    return collection


def upsert_batch(collection: Collection, ids: List[int], vecs: List[List[float]]) -> None:
    """
    Milvus 2.3 的 PyMilvus 没有真正意义上的 upsert，这里用 insert + flush，
    并要求你首次导入前清空 collection（或者保证 id 不重复）。
    """
    data = [ids, vecs]
    collection.insert(data)


def main() -> None:
    milvus_uri = os.getenv("MILVUS_URI", "http://127.0.0.1:19530")
    collection_name = os.getenv("MILVUS_COLLECTION", "picture_vectors")
    batch_size = int(os.getenv("BATCH_SIZE", "128"))

    print(f"[milvus] connect uri={milvus_uri}")
    connections.connect(alias="default", uri=milvus_uri)

    print("[embedder] loading...")
    embedder = load_embedder()

    # 这个模型默认输出 384 维（paraphrase-multilingual-MiniLM-L12-v2）
    test_vec = embed_text(embedder, ["test"])[0]
    dim = len(test_vec)
    print(f"[embedder] dim={dim}")

    collection = ensure_collection(collection_name, dim=dim)
    print(f"[milvus] using collection={collection_name}")

    conn = get_mysql_conn()
    try:
        buf_ids: List[int] = []
        buf_texts: List[str] = []
        total = 0

        for row in iter_pictures(conn):
            pid = int(row["id"])
            text = build_text(row)
            if not text.strip():
                continue
            buf_ids.append(pid)
            buf_texts.append(text)

            if len(buf_ids) >= batch_size:
                vecs = embed_text(embedder, buf_texts)
                upsert_batch(collection, buf_ids, vecs)
                total += len(buf_ids)
                print(f"[sync] inserted {total}")
                buf_ids, buf_texts = [], []

        if buf_ids:
            vecs = embed_text(embedder, buf_texts)
            upsert_batch(collection, buf_ids, vecs)
            total += len(buf_ids)
            print(f"[sync] inserted {total}")

        collection.flush()
        print("[done] flush ok")
    finally:
        conn.close()


if __name__ == "__main__":
    main()


