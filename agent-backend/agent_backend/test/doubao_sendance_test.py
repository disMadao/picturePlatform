import os
import time  
# 通过 pip install 'volcengine-python-sdk[ark]' 安装方舟SDK
from volcenginesdkarkruntime import Ark

# 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
# 初始化Ark客户端，从环境变量中读取您的API Key
client = Ark(
    # 此为默认路径，您可根据业务所在地域进行配置
    base_url="https://ark.cn-beijing.volces.com/api/v3",
    # 从环境变量中获取您的 API Key。此为默认方式，您可根据需要进行修改
    api_key=os.environ.get("ARK_API_KEY"),
)

if __name__ == "__main__":
    print("----- create request -----")
    create_result = client.content_generation.tasks.create(
        model="doubao-seedance-1-5-pro-251215", # 模型 Model ID 已为您填入
        content=[
            {
                # 文本提示词与参数组合
                "type": "text",
                "text": "这是马男波杰克中的经典一幕，让图片动起来，让人物用中文读出其中的语句，动作要连贯，图片上半部分是马男先说话，下半部分才到卡洛琳在说话，注意，人物要保持正常语速把对话念完，如果时间不够也要按照顺序念，可以少，不能跳!  --camerafixed false --watermark true"
            },
            { # 若仅需使用文本生成视频功能，可对该大括号内的内容进行注释处理，并删除上一行中大括号后的逗号。
                # 首帧图片URL
                "type": "image_url",
                "image_url": {
                    "url": "https://mei-1-1318186176.cos.ap-nanjing.myqcloud.com/public/1913190855008186369/2025-09-07_OXPJXokj2hv3eAcU.webp" 
                }
            }
        ]
    )
    print(create_result)

    # 轮询查询部分
    print("----- polling task status -----")
    task_id = create_result.id
    while True:
        get_result = client.content_generation.tasks.get(task_id=task_id)
        status = get_result.status
        if status == "succeeded":
            print("----- task succeeded -----")
            print(get_result)
            break
        elif status == "failed":
            print("----- task failed -----")
            print(f"Error: {get_result.error}")
            break
        else:
            print(f"Current status: {status}, Retrying after 3 seconds...")
            time.sleep(3)

# 更多操作请参考下述网址
# 查询视频生成任务列表：https://www.volcengine.com/docs/82379/1521675
# 取消或删除视频生成任务：https://www.volcengine.com/docs/82379/1521720