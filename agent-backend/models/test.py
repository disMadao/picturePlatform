# 创建 download_model.py 文件
from huggingface_hub import snapshot_download

snapshot_download(
    repo_id="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
    local_dir="./paraphrase-multilingual-MiniLM-L12-v2",
    local_dir_use_symlinks=False,
    resume_download=True,  # 支持断点续传
    max_workers=4  # 多线程下载
)