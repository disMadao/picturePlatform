"""
快速性能基准测试脚本

无需安装额外依赖，直接运行即可得到性能基准数据
使用方法: python quick_benchmark.py

会测试：
1. Java 后端普通搜索的 QPS 和延迟
2. Agent 智能检索的 QPS 和延迟
3. 并发压力测试
"""

import time
import statistics
import concurrent.futures
import threading
from typing import List, Dict, Callable
import json

try:
    import requests
except ImportError:
    print("请先安装 requests: pip install requests")
    exit(1)


# ============ 配置 ============
JAVA_BASE_URL = "http://localhost:8123/api"
AGENT_BASE_URL = "http://localhost:9002"

SEARCH_KEYWORDS = ["风景", "美女", "动漫", "壁纸", "自然", "城市"]
SEMANTIC_QUERIES = ["找一张温暖治愈的风景图", "推荐一些适合做壁纸的图片"]


# ============ 测试函数 ============
_thread_local = threading.local()


def _get_session() -> requests.Session:
    session = getattr(_thread_local, "session", None)
    if session is None:
        session = requests.Session()
        _thread_local.session = session
    return session


def test_java_search(keyword: str = "风景") -> Dict:
    """测试 Java 后端普通搜索"""
    start = time.perf_counter()
    try:
        resp = _get_session().post(
            f"{JAVA_BASE_URL}/picture/list/page/vo",
            json={"current": 1, "pageSize": 20, "searchText": keyword},
            timeout=10
        )
        elapsed = (time.perf_counter() - start) * 1000
        return {
            "success": resp.status_code == 200 and resp.json().get("code") == 0,
            "latency_ms": elapsed,
            "status_code": resp.status_code
        }
    except Exception as e:
        return {
            "success": False,
            "latency_ms": (time.perf_counter() - start) * 1000,
            "error": str(e)
        }


def test_agent_vector_search(query: str = "找一张温暖治愈的风景图") -> Dict:
    """测试 Agent 向量搜索"""
    start = time.perf_counter()
    try:
        resp = _get_session().post(
            f"{AGENT_BASE_URL}/agent/search",
            json={"query_text": query, "mode": "vector_text", "top_k": 10},
            timeout=60  # 向量搜索可能较慢
        )
        elapsed = (time.perf_counter() - start) * 1000
        data = resp.json()
        print("data:", data)
        return {
            "success": resp.status_code == 200 and data.get("code") == 0,
            "latency_ms": elapsed,
            "status_code": resp.status_code,
            "result_count": len(data.get("data", {}).get("pictures", []))
        }
    except Exception as e:
        return {
            "success": False,
            "latency_ms": (time.perf_counter() - start) * 1000,
            "error": str(e)
        }


def test_agent_backend_search(keyword: str = "风景") -> Dict:
    """测试 Agent 走后端搜索"""
    start = time.perf_counter()
    try:
        resp = _get_session().post(
            f"{AGENT_BASE_URL}/agent/search",
            json={"query_text": keyword, "mode": "backend", "top_k": 20},
            timeout=30
        )
        elapsed = (time.perf_counter() - start) * 1000
        return {
            "success": resp.status_code == 200 and resp.json().get("code") == 0,
            "latency_ms": elapsed,
            "status_code": resp.status_code
        }
    except Exception as e:
        return {
            "success": False,
            "latency_ms": (time.perf_counter() - start) * 1000,
            "error": str(e)
        }


# ============ 基准测试 ============
def run_sequential_benchmark(test_func: Callable, name: str, iterations: int = 20):
    """串行基准测试 - 测量单请求延迟"""
    print(f"\n{'='*60}")
    print(f"📊 {name} - 串行基准测试 ({iterations} 次请求)")
    print('='*60)
    
    latencies = []
    successes = 0
    
    for i in range(iterations):
        result = test_func()
        latencies.append(result["latency_ms"])
        if result["success"]:
            successes += 1
        print(f"  请求 {i+1:2d}: {result['latency_ms']:7.2f}ms {'✓' if result['success'] else '✗'}")
    
    print(f"\n📈 统计结果:")
    print(f"  成功率: {successes}/{iterations} ({100*successes/iterations:.1f}%)")
    print(f"  平均延迟: {statistics.mean(latencies):.2f}ms")
    print(f"  中位数延迟: {statistics.median(latencies):.2f}ms")
    print(f"  P95 延迟: {sorted(latencies)[int(len(latencies)*0.95)]:.2f}ms")
    print(f"  最小/最大: {min(latencies):.2f}ms / {max(latencies):.2f}ms")
    print(f"  理论 QPS (串行): {1000/statistics.mean(latencies):.1f}")
    
    return latencies


def run_concurrent_benchmark(test_func: Callable, name: str,
                              concurrent_users: int = 10,
                              requests_per_user: int = 5):
    """并发压力测试 - 测量系统吞吐量"""
    print(f"\n{'='*60}")
    print(f"🔥 {name} - 并发压力测试")
    print(f"   并发用户: {concurrent_users}, 每用户请求数: {requests_per_user}")
    print('='*60)
    
    total_requests = concurrent_users * requests_per_user
    results = []

    def _user_worker() -> List[Dict]:
        user_results = []
        for _ in range(requests_per_user):
            user_results.append(test_func())
        return user_results

    start_time = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrent_users) as executor:
        futures = [executor.submit(_user_worker) for _ in range(concurrent_users)]
        for future in concurrent.futures.as_completed(futures):
            results.extend(future.result())
    
    total_time = time.perf_counter() - start_time
    
    successes = sum(1 for r in results if r["success"])
    latencies = [r["latency_ms"] for r in results]
    
    print(f"\n📈 统计结果:")
    print(f"  总请求数: {total_requests}")
    print(f"  成功请求: {successes} ({100*successes/total_requests:.1f}%)")
    print(f"  总耗时: {total_time:.2f}s")
    print(f"  实际 QPS: {total_requests/total_time:.1f}")
    print(f"  平均延迟: {statistics.mean(latencies):.2f}ms")
    print(f"  P95 延迟: {sorted(latencies)[int(len(latencies)*0.95)]:.2f}ms")
    print(f"  P99 延迟: {sorted(latencies)[int(len(latencies)*0.99)]:.2f}ms")
    
    return {
        "qps": total_requests/total_time,
        "success_rate": successes/total_requests,
        "avg_latency": statistics.mean(latencies),
        "p95_latency": sorted(latencies)[int(len(latencies)*0.95)]
    }


def find_max_qps(test_func: Callable, name: str, target_success_rate: float = 0.95, concurrent_users_nums : List = [1, 2, 5, 10, 20, 50]):
    """逐步增加并发，找到系统最大 QPS"""
    print(f"\n{'='*60}")
    print(f"🎯 {name} - 寻找最大 QPS (目标成功率 ≥ {target_success_rate*100}%)")
    print('='*60)
    
    max_qps = 0
    best_concurrent = 0
    
    for concurrent_users in concurrent_users_nums:
        print(f"\n测试并发数: {concurrent_users}")
        result = run_concurrent_benchmark(test_func, name, concurrent_users, 10)
        
        if result["success_rate"] >= target_success_rate:
            if result["qps"] > max_qps:
                max_qps = result["qps"]
                best_concurrent = concurrent_users
        else:
            print(f"  ⚠️ 成功率低于 {target_success_rate*100}%, 停止增加并发")
            break
    
    print(f"\n🏆 最佳配置: 并发数={best_concurrent}, 最大QPS={max_qps:.1f}")
    return max_qps, best_concurrent


# ============ 主程序 ============
def main():
    print("="*60)
    print("     图片搜索系统性能基准测试")
    print("="*60)
    
    # 1. 检查服务可用性
    print("\n🔍 检查服务可用性...")
    
    java_ok = False
    agent_ok = False
    
    try:
        resp = requests.get(f"{JAVA_BASE_URL}/../health", timeout=5)
        java_ok = True
        print(f"  ✓ Java 后端 ({JAVA_BASE_URL}) 可用")
    except Exception as e:
        print(f"  ✗ Java 后端 ({JAVA_BASE_URL}) 不可用: {e}")
    
    try:
        resp = requests.get(f"{AGENT_BASE_URL}/health", timeout=5)
        agent_ok = resp.status_code == 200
        print(f"  ✓ Agent 后端 ({AGENT_BASE_URL}) 可用")
    except Exception as e:
        print(f"  ✗ Agent 后端 ({AGENT_BASE_URL}) 不可用: {e}")
    
    # 2. Java 后端测试
    if java_ok:
        run_sequential_benchmark(test_java_search, "Java 普通搜索", 10)
        run_concurrent_benchmark(test_java_search, "Java 普通搜索", 10, 10)
    
    # 3. Agent 后端测试
    if agent_ok:
        run_sequential_benchmark(test_agent_backend_search, "Agent->Java 后端搜索", 10)
        run_sequential_benchmark(test_agent_vector_search, "Agent 向量搜索", 5)  # 向量搜索较慢，减少次数
        
        print("\n⚠️ 向量搜索并发测试可能较慢，请耐心等待...")
        run_concurrent_benchmark(test_agent_vector_search, "Agent 向量搜索", 3, 3)
    
    # 4. 总结
    print("\n" + "="*60)
    print("📋 测试完成！建议:")
    print("="*60)
    print("""
1. 如果 Java 普通搜索延迟 < 50ms，说明缓存工作正常
2. 如果向量搜索延迟 > 500ms，建议：
   - 使用 GPU 加速 SentenceTransformer
   - 或部署多个 Agent 实例做负载均衡
3. 使用 Locust 进行更专业的压力测试：
   locust -f locustfile.py --host=http://localhost:8123
""")


if __name__ == "__main__":
    # main()
    # print()
    concurrent_users_nums_for_agent_vector_search = [2]# [600,700,800,900]
    print(find_max_qps(test_agent_vector_search, "Agent 向量搜索", 0.95,concurrent_users_nums_for_agent_vector_search))
    concurrent_users_nums_for_java_search = [100,200,300,400,500,600,700,800,900,1000]
    # print(find_max_qps(test_java_search, "Java 普通搜索", 0.95, concurrent_users_nums_for_java_search))
    concurrent_users_nums_for_agent_backend_search =  [100,200,300,400,500,600,700,800,900,1000]
    # print(find_max_qps(test_agent_backend_search, "Agent->Java 后端搜索", 0.95,concurrent_users_nums_for_agent_backend_search))

