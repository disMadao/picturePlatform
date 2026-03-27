from pymilvus import connections, utility, Collection

# 连接本地 Milvus
connections.connect(host="127.0.0.1", port="19530")

# 1. 检查是否存在 Collection
collections = utility.list_collections()
print(f"当前所有集合: {collections}")

# 2. 如果知道名字（假设叫 picture），检查数量
if "picture_vectors" in collections:
    col = Collection("picture_vectors")
    col.load()
    print(f"实体总数: {col.num_entities}")
else:
    print("警告：库中根本没有名为 'picture' 的集合！")