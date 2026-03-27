"""
压力测试脚本 - 使用 Locust 测试图片搜索系统性能

使用方法：
1. 安装依赖: pip install locust
2. 启动测试: locust -f locustfile.py --host=http://localhost:8123
3. 打开浏览器访问 http://localhost:8089 配置并发用户数

测试场景：
- 普通搜索：Java 后端 /api/picture/list/page/vo
- 智能检索：Agent 后端 /agent/search
"""

import random
from locust import HttpUser, task, between, events
import time


# ============ 测试数据 ============
SEARCH_KEYWORDS = [
    "风景", "美女", "动漫", "壁纸", "自然", "城市",
    "猫", "狗", "花", "海洋", "山", "日落",
    "抽象", "艺术", "科技", "建筑", "食物", "运动"
]

SEMANTIC_QUERIES = [
    "找一张温暖治愈的风景图",
    "类似梵高风格的油画",
    "推荐一些适合做手机壁纸的图片",
    "有没有小清新风格的照片",
    "找相似的日系风格图片",
]


# ============ Java 后端普通搜索测试 ============
class JavaBackendUser(HttpUser):
    """
    测试 Java 后端普通搜索性能
    """
    wait_time = between(0.1, 0.5)  # 模拟用户思考时间
    
    @task(10)
    def search_pictures_cached(self):
        """测试缓存命中场景 - 使用固定关键词"""
        keyword = "风景"  # 固定关键词，容易命中缓存
        self.client.post(
            "/api/picture/list/page/vo",
            json={
                "current": 1,
                "pageSize": 20,
                "searchText": keyword
            },
            name="[Java] 普通搜索(缓存)"
        )
    
    @task(5)
    def search_pictures_random(self):
        """测试随机关键词 - 模拟真实场景"""
        keyword = random.choice(SEARCH_KEYWORDS)
        page = random.randint(1, 5)
        self.client.post(
            "/api/picture/list/page/vo",
            json={
                "current": page,
                "pageSize": 20,
                "searchText": keyword
            },
            name="[Java] 普通搜索(随机)"
        )


# ============ Agent 智能检索测试 ============
class AgentSearchUser(HttpUser):
    """
    测试 Agent 智能检索性能（向量搜索）
    """
    host = "http://localhost:9002"  # Agent 后端地址
    wait_time = between(0.5, 2)  # 智能检索用户等待时间更长
    
    @task(10)
    def vector_text_search(self):
        """测试向量文本搜索 - 强制走向量库"""
        query = random.choice(SEMANTIC_QUERIES)
        self.client.post(
            "/agent/search",
            json={
                "query_text": query,
                "mode": "vector_text",  # 强制向量搜索
                "top_k": 10
            },
            name="[Agent] 向量文本搜索"
        )
    
    @task(5)
    def backend_search_via_agent(self):
        """测试通过 Agent 走后端搜索"""
        keyword = random.choice(SEARCH_KEYWORDS)
        self.client.post(
            "/agent/search",
            json={
                "query_text": keyword,
                "mode": "backend",  # 强制走后端
                "top_k": 20
            },
            name="[Agent] 后端搜索"
        )
    
    @task(3)
    def auto_mode_search(self):
        """测试自动模式 - 让 Agent 自行决策"""
        query = random.choice(SEARCH_KEYWORDS + SEMANTIC_QUERIES)
        self.client.post(
            "/agent/search",
            json={
                "query_text": query,
                "mode": "auto",
                "top_k": 10
            },
            name="[Agent] 自动模式"
        )


# ============ 混合场景测试 ============
class MixedUser(HttpUser):
    """
    混合场景：80% 普通搜索 + 20% 智能检索
    更贴近真实用户行为
    """
    wait_time = between(0.2, 1)
    
    @task(8)
    def java_search(self):
        """普通搜索"""
        keyword = random.choice(SEARCH_KEYWORDS)
        self.client.post(
            "/api/picture/list/page/vo",
            json={
                "current": 1,
                "pageSize": 20,
                "searchText": keyword
            },
            name="[混合] Java搜索"
        )
    
    @task(2)
    def agent_vector_search(self):
        """智能检索"""
        # Agent 后端在不同端口，需要手动指定
        import requests
        query = random.choice(SEMANTIC_QUERIES)
        start = time.time()
        try:
            resp = requests.post(
                "http://localhost:9002/agent/search",
                json={
                    "query_text": query,
                    "mode": "vector_text",
                    "top_k": 10
                },
                timeout=30
            )
            elapsed = (time.time() - start) * 1000
            if resp.status_code == 200:
                events.request.fire(
                    request_type="POST",
                    name="[混合] Agent向量搜索",
                    response_time=elapsed,
                    response_length=len(resp.content),
                    exception=None,
                    context={}
                )
            else:
                events.request.fire(
                    request_type="POST",
                    name="[混合] Agent向量搜索",
                    response_time=elapsed,
                    response_length=0,
                    exception=Exception(f"Status {resp.status_code}"),
                    context={}
                )
        except Exception as e:
            events.request.fire(
                request_type="POST",
                name="[混合] Agent向量搜索",
                response_time=(time.time() - start) * 1000,
                response_length=0,
                exception=e,
                context={}
            )

