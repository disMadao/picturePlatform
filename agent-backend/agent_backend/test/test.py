import requests

def test_agent_api():
    base_url = "http://118.195.165.9:9002"
    
    # 1. 测试健康检查
    try:
        resp = requests.get(f"{base_url}/health", timeout=3)
        print(f"健康检查: {resp.status_code} - {resp.json()}")
    except Exception as e:
        print(f"健康检查失败: {e}")
        return False
    
    # 2. 测试搜索接口（最小参数）
    try:
        data = {"user_id": 1, "query_text": "风景图片", "top_k": 2}
        resp = requests.post(f"{base_url}/agent/search", json=data, timeout=5)
        print(f"搜索接口: {resp.status_code}")
        if resp.status_code == 200:
            result = resp.json()
            print(f"返回码: {result.get('code')}, 消息: {result.get('message')}")
            return True
        else:
            print(f"错误: {resp.text}")
            return False
    except Exception as e:
        print(f"搜索接口失败: {e}")
        return False

if __name__ == "__main__":
    test_agent_api()