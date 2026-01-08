from dataclasses import dataclass
from typing import List, Optional
import logging
import os

from pymilvus import MilvusClient, Collection, connections, utility


logger = logging.getLogger(__name__)


@dataclass
class VectorSearchResult:
    """
    向量搜索结果类型。
    """

    picture_id: int
    score: float


class VectorStoreClient:
    """
    基于 Milvus 的向量数据库客户端骨架。
    这里只写好接口和字段约定，真正的向量编码与 search 逻辑留给你根据实际 Milvus 集群实现。
    """

    def __init__(
        self,
        uri: str = "http://localhost:19530",
        user: Optional[str] = None,
        password: Optional[str] = None,
        collection_name: str = "picture_vectors",
        id_field: str = "picture_id",
        vector_field: str = "embedding",
    ) -> None:
        # 允许从环境变量覆盖，方便在 docker / 服务器环境配置
        uri = os.getenv("MILVUS_URI", uri)
        user = os.getenv("MILVUS_USER", user or "") or None
        password = os.getenv("MILVUS_PASSWORD", password or "") or None

        # 本地可能暂时没有 Milvus：连接失败时不要阻塞 Agent 启动
        self._client: Optional[MilvusClient]
        self._collection: Optional[Collection] = None
        try:
            self._client = MilvusClient(uri=uri, user=user, password=password)  # type: ignore[arg-type]
            # 这里再补一层连接，供 Collection/utility 使用
            connections.connect(alias="default", uri=uri, user=user or "", password=password or "")
            if utility.has_collection(collection_name):
                self._collection = Collection(collection_name)
                try:
                    self._collection.load()
                except Exception:
                    # load 失败也不影响启动，实际 search 时再看
                    pass
        except Exception as e:
            logger.warning("Milvus 连接失败（uri=%s），向量搜索将返回空结果：%s", uri, e)
            self._client = None
            self._collection = None
        self.collection_name = collection_name
        self.id_field = id_field
        self.vector_field = vector_field

        self._embedder = None

    def _get_embedder(self):
        """
        懒加载文本向量模型。
        为了不强依赖 torch，这里只有在你真正启用向量搜索时才会加载。
        """
        if self._embedder is not None:
            return self._embedder
        model_name = os.getenv(
            "EMBEDDING_MODEL", "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
        )
        try:
            from sentence_transformers import SentenceTransformer  # type: ignore
        except Exception as e:
            logger.warning(
                "未安装 sentence-transformers/torch，向量搜索暂不可用，将返回空结果。错误：%s",
                e,
            )
            self._embedder = None
            return None
        self._embedder = SentenceTransformer(model_name)
        return self._embedder

    def search_by_text(self, text: str, top_k: int = 20) -> List[VectorSearchResult]:
        """
        文本向量搜索（占位实现）。

        TODO：
        1）将 text 编码为向量（如调用你自己的文本编码模型）；
        2）调用 self._client.search(...)，从 Milvus 中按相似度查询；
        3）根据返回结果构造 VectorSearchResult 列表（picture_id + score）。
        """
        if self._collection is None:
            return []
        embedder = self._get_embedder()
        if embedder is None:
            return []

        try:
            vec = embedder.encode([text], normalize_embeddings=True)[0].tolist()
            search_params = {"metric_type": "COSINE", "params": {"nprobe": 10}}
            res = self._collection.search(
                data=[vec],
                anns_field=self.vector_field,
                param=search_params,
                limit=top_k,
            )
            results: List[VectorSearchResult] = []
            for hit in (res[0] or []):
                # hit.id 是主键（picture_id）
                results.append(VectorSearchResult(picture_id=int(hit.id), score=float(hit.distance)))
            return results
        except Exception as e:
            logger.warning("Milvus 文本向量检索失败，将返回空结果：%s", e)
            return []

    def search_by_image(
        self,
        image_url: Optional[str] = None,
        image_bytes: Optional[bytes] = None,
        top_k: int = 20,
    ) -> List[VectorSearchResult]:
        """
        以图搜图（占位实现）。

        TODO：
        1）根据 image_url 或 image_bytes 生成图片向量；
        2）调用 self._client.search(...)；
        3）返回 VectorSearchResult 列表。
        """
        # TODO：图片向量检索需要图片 encoder（如 CLIP），这里先返回空列表
        return []


vector_client = VectorStoreClient()

