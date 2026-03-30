# WebSocket

[toc]

长链接，不用频繁询问有无数据。



## websocket和http

"http是单向的"，一问一答，答完就断；WebSocket是双向的，

websocket是一种应用层协议，基于TCP（全双工），提供全双工、持久连接的通信能力。

实际上前面说http是单向的也是打引号的，严格来说取决于http的版本：

- 对于 http/1.0 ：单工，短链接，客户端收到信息之后就断开了。
- 对于 http/1.x ： **半双工**单向的。
- 对于 http/2 和http/3：支持多路复用的。

**http能做到服务器主动向客户端发送数据吗？**



### 扫码登录场景

http做的伪长连接场景：用户扫描二维码后，前端不断的发送请求到服务器看用户扫码了没有，保证1~2s内被处理。

会增加下游服务器负担和增加带宽。

扫码登录解决办法1——长轮询：

用户发送一次请求，等待服务器端返回响应，只是将超时时间设置的很大，这样就不用一直发了。



### 服务器推送技术

这种在用户无感知的情况下，服务器向用户发送数据的方式叫做服务器推送技术。

扫码这种简单场景还能用，如果是游戏，服务器需要主动推送大量数据到客户端。

这就用到了websocket了，http用的是全双工的tcp协议，但设计之初是主要考虑的文本场景，改成了半双工。

打开网页会涉及到很多场景，所以所有连接开始时候都是用的http协议，如果浏览器需要升级协议到websocket，会带一个特殊的http header去升级。服务器返回一个101状态码。

然后走websocket握手流程。





## WebSocket与SSE

Server-Sent-Events是一种从服务器到客户端的单向数据传输技术。基于HTTP实现，连接建立后，服务器就可以持续的传输数据到客户端，不需要客户端重复发起请求。

AI大模型中使用SSE而不是websocket，websocket在需要真正实时双向交互的场景下更好。而AI应用场景下，SSE更加简单、高效。

具体有：

- 实现成本低：SSE基于标准的HTTP，实现简单，而websocket还需要专门的连接管理和握手过程。
- 通信模式：ai应用主要是服务器向客户端单向推送，和SSE 的单向推送模型契合。websocket不怎么需要双向能力
- 内置机制：SSE包含了自动重连 和事件ID机制（从断掉地方重传）
- 资源消耗：一般来说，SSE更加轻量级，websocket需要简历和维护一个比较长的TCP连接。

















curl -X POST 'https://job.xiaohongshu.com/websiterecruit/apply/apply' \
  -H 'accept: application/json, text/plain, */*' \
  -H 'accept-encoding: gzip, deflate, br, zstd' \
  -H 'accept-language: zh-CN,zh;q=0.9' \
  -H 'authorization: NGE1ODc3NTJlZWY3NGE1YTkzMjU5MWI2Nzg0OTE3YzM=' \
  -H 'content-type: application/json' \
  -H 'cookie: abRequestId=e5f91265-5009-5ccd-8a54-9142a30b050c; a1=19a2a4c66c8c827y8b05xmnvfc3aht4s4n9pqn6cf50000413101; webId=5090d456e07b3731d57a1727f40d8ed0; gid=yj0J04DKj84jyj0J04SKKIIKSYSYJWvYD82CjSi6IhiSqU280x94MA8884yqy8y8j8qYY0Sy; web_session=0400698f01a38756e3663f60223b4bb0828b82; ets=1774503508876; webBuild=6.2.2; unread={%22ub%22:%2269c261100000000021004c13%22%2C%22ue%22:%2269a3e8c9000000000d0098e2%22%2C%22uc%22:54}; xsecappid=ats-website; acw_tc=0a0d01fc17746268286686805e8bee0dda8941f99e4921e779a7b9054fedeb; loadts=1774627567235; websectiga=9730ffafd96f2d09dc024760e253af6ab1feb0002827740b95a255ddf6847fc8; sec_poison_id=a1ce3877-1ebe-43ce-afaa-84ada5a6b969' \
  -H 'origin: https://job.xiaohongshu.com' \
  -H 'priority: u=1, i' \
  -H 'referer: https://job.xiaohongshu.com/campus/position/19192/apply' \
  -H 'sec-ch-ua: "Chromium";v="146", "Not-A.Brand";v="24", "Microsoft Edge";v="146"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "Windows"' \
  -H 'sec-fetch-dest: empty' \
  -H 'sec-fetch-mode: cors' \
  -H 'sec-fetch-site: same-origin' \
  -H 'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0' \
  -H 'x-b3-traceid: 65f28336d20f27cc' \
  -H 'x-s: XYS_2UQhPsHCH0c1PjhhHjIj2erjwjQY4oPT49pjq9SF8aHVHdWUH0ijp9S18BR7qUHVHdWAH0ijJnEAPerIPpRyqe8D2ep38aTtzbzyqbbbnaReL741pFQzPBb/ydzO/DR/4oSHPDk9LMqA8fk38MHU4dY14BSHt9peLS+IPdD3tFI9J94/cFVELMkhGLVl+9k/4rRt8fV3PFzhzgSPqeG9t9RByM+gpeQE4Bb9anMB8eSBP0YytFi3+D8a2nSkLnlbJBbsyFYBLpz1y0QjwnT/87i6nd8VwbmLcFMt8gzSJDlEyfhFarYfwnPjNsQh+sHCHfRjyfp04sQR' \
  -H 'x-s-common: 2UQAPsHC+aIjqArjwjHjNsQhPsHCH0rjNsQhPaHCH0c1PjhhHjIj2eHjwjQgynEDJ74AHjIj2ePjwjQY4oPT49pjq9SF8aHVHdWFH0ijPahIN0rjNsQh+aHCH0rEG/QY+BP9+fPhGAWU+7DhG0ZM2BM14f80P9bi4ezA+BhEqob1+f+f+/ZIPeZFP/PlPerjNsQh+jHCHjHVHdW7H0ijHjIj2eWjwjQQPAYUaBzdq9k6qB4Q4fpA8b878FSet9RQzLlTcSiM8/+n4MYP8F8LagY/P9Ql4FpUzfpS2BcI8nT1GFbC/L88JdbFyrSiafp/8BThqgb78rS9cg+gcf+i4MmF4B4T+e8NpgkhanWIqAmPa7+xqg412/4rnDS9J7+hGSmx2pkMcLSia9prG/4A8SpLprkl4bH3qg4mqBzI/DSeyBMwa/YN2S87LFSe89p34gzH47b7zrSbzdbQzaRAprSyyLShqDMQ4f4S8ob7LjV7qbmCnDEA8bDA8n8l4rbQyFESPM8787bl4omI4gzha7kdqAbgqBpQcM8ganYzPsRc4bbNpd4ma/+yPfRT8Bpkqg4faL+m8pzn4oQQzaV3aLpTJf+f8Bpx87k8qfR6q98l4FRyp9RS8rlrzrQ687+xndmsagYNq9zn4BbQy78S8db7LfQ+/rSo80zsa/P7q7Yl4rL6pFRS2emV+rSiLg+Qz/W3LnzypLShJpmO2fM6anS0nBpc4F8Q4fSePDQ9qFzC+7+hpdzDagG98nc7+9p8ySQoanSD8/8gGDcUpd4xanYtqA+68gP9zo8SpbmF/f+p+fpr4gqMag8887km+npDqg4mqBG6q98c47QQPMc6agYTJo+l4o+YLo4Eq7+HGSkm4fLAqsRSzbm72rSe8g+3zemSL9pHyLSk+7+xGfRAP94UzDSk8BL94gqAanSUy7zM4BMF4gzBagYS8pzc4r8QyrkSyp8FJrS389LILoz/t7b7Lokc4MpQ4fY3agY0q0zdarr3aLESypmFyDSiqdzQyBRAydbFLrlILnb7qDTA8B808rSi2juU4g4yqdp7LFSe8o+3Loz/tFMN8/b0cg+LzflEanYwq7WE+7+3LozoJdp7/FSePBpgqg4banV98p8SpFlQc9zSng+98p+l4eSQPA4Apdp74L4dLnbQzgQNagG6q9TPPBpL808SzrcAqFzn4BRQypmia/+LnoQn4bbQ4f+iaLpwqA+BqLb7NURAL7pFGLS3LdQT4gclz7kw8LzyPo+/4gz687pFJLDAzBQQzLL38pmF8pmM4URQ2o8ApfF78pzM4UT04gc3anYjwBpn4bmQypzTarSM2rDA8oPAqo8AyL8d8Lzc47YTqgzmagYg8rS3J7+8qgzpanYw8nz/LfTQcFp9GS48JFSeyFRQ40pS+db7yeQn4BEzLozVa/+PprSkcg+3pAFRHjIj2eDjw0WM+eqIP/c9PaIj2erIH0iINsQhP/rjwjQ1J7QTGnIjKc==' \
  -H 'x-t: 1774628122585' \
  -d '{
    "applyType": "campus",
    "accountId": 601451,
    "positionId": 19192,
    "positionName": "社区Agent开发实习生（AI Coding方向）-27届/28届（有转正机会）",
    "jobProjectName": null,
    "positionWorkplace": "北京市，上海市",
    "jobTypeDesc": "客户端开发",
    "talentId": 1636204,
    "interviewId": 6443297,
    "status": "resume_choose_doing",
    "interviewStatusColor": "#00000073",
    "resumeId": 10072349,
    "resumeUrl": "https://ep-ehr-s1.xhscdn.com/recruit/1040g28g31u7mhpb7gu0000000000ibbbgi5pa88.pdf?sign=b9af8fa18d6b35d1bcde6cd799e560ca&t=69c7fc28",
    "resumeName": "枚子君-社区Agent开发实习生（AI Coding方向）-27届/28届（有转正机会）-简历",
    "attachmentUrl": null,
    "attachmentName": null,
    "name": "枚子君",
    "mobile": "+86 17773045931",
    "email": "2544431082@qq.com",
    "gender": null,
    "adaptable": true,
    "applyTime": "2026-03-27T16:54:37.000+0800",
    "birthday": "2002-06",
    "workplaceIntentionList": [
        "3100",
        "1100"
    ],
    "highestEducation": "硕士研究生",
    "highestEducationCollege": "南京大学",
    "graduationDate": "2027-06",
    "resumeVo": {
        "resumeId": 10072349,
        "resumeName": "枚子君-社区Agent开发实习生（AI Coding方向）-27届/28届（有转正机会）-简历",
        "name": "枚子君",
        "gender": null,
        "age": 24,
        "birthday": "2002-06",
        "mobile": "+86 17773045931",
        "email": "2544431082@qq.com",
        "graduationDate": "2027-06",
        "workYears": 1,
        "undergraduateCollegeName": null,
        "highestEducation": "硕士研究生",
        "highestEducationCollege": "南京大学",
        "highestEducationMajor": "软件工程",
        "selfEvaluation": null,
        "jobIntention": "研发实习",
        "workplaceIntention": "上海市,北京市",
        "salaryIntention": null,
        "recentCompany": "苏宁易购集团股份有限公司",
        "recentJob": "java开发",
        "resumeUrl": "https://ep-ehr-s1.xhscdn.com/recruit/1040g28g31u7mhpb7gu0000000000ibbbgi5pa88.pdf?sign=b9af8fa18d6b35d1bcde6cd799e560ca&t=69c7fc28",
        "resumeInfo": null,
        "attachmentUrl": null,
        "resumeContent": null,
        "createTime": null,
        "publications": [],
        "publicationDesc": null,
        "awards": [],
        "awardDesc": null,
        "workLink": null,
        "githubStar": null,
        "certificates": [],
        "avatarUrl": "https://xhs-ehr-static.xhscdn.com/uinfocollect/ats_avatar_2dc1119e3d4b4588a63e8e7259ebbdaf.jpg",
        "resumeEducationInfo": [
            {
                "resumeId": 10072349,
                "schoolName": "南京大学",
                "schoolRank": 0,
                "schoolType": 0,
                "startDate": "2025-09",
                "endDate": "2027-06",
                "isTongzhao": true,
                "major": "软件工程",
                "majorRank": 0,
                "toPresent": false,
                "gpa": null,
                "eduNature": null,
                "degree": "硕士研究生",
                "collegeName": null,
                "city": "南京",
                "country": "中国",
                "internationalRanking": 0,
                "province": "江苏",
                "tags": "erben_above",
                "level": "985",
                "id": 19193508,
                "normalizedSchoolName": "南京大学",
                "laboratory": "",
                "researchDirection": "",
                "scoreRank": "",
                "mentor": "",
                "setSchoolName": true,
                "setSchoolRank": true,
                "setSchoolType": true,
                "setIsTongzhao": true,
                "setMajor": true,
                "setMajorRank": true,
                "setToPresent": true,
                "setGpa": false,
                "setEduNature": false,
                "setDegree": true,
                "setCity": true,
                "setCountry": true,
                "setInternationalRanking": true,
                "setProvince": true,
                "setLevel": true,
                "setNormalizedSchoolName": true,
                "setLaboratory": true,
                "setResearchDirection": true,
                "setScoreRank": true,
                "setMentor": true,
                "setId": true,
                "setTags": true,
                "setStartDate": true,
                "setEndDate": true,
                "setResumeId": true,
                "setCollegeName": false
            },
            {
                "resumeId": 10072349,
                "schoolName": null,
                "schoolRank": 0,
                "schoolType": 0,
                "startDate": "2020-09",
                "endDate": "2024-06",
                "isTongzhao": true,
                "major": "计算机科学与技术",
                "majorRank": 0,
                "toPresent": false,
                "gpa": null,
                "eduNature": null,
                "degree": "本科",
                "collegeName": null,
                "city": null,
                "country": null,
                "internationalRanking": 0,
                "province": null,
                "tags": null,
                "level": null,
                "id": 19193509,
                "normalizedSchoolName": null,
                "laboratory": "",
                "researchDirection": "",
                "scoreRank": "",
                "mentor": "",
                "setSchoolName": false,
                "setSchoolRank": true,
                "setSchoolType": true,
                "setIsTongzhao": true,
                "setMajor": true,
                "setMajorRank": true,
                "setToPresent": true,
                "setGpa": false,
                "setEduNature": false,
                "setDegree": true,
                "setCity": false,
                "setCountry": false,
                "setInternationalRanking": true,
                "setProvince": false,
                "setLevel": false,
                "setNormalizedSchoolName": false,
                "setLaboratory": true,
                "setResearchDirection": true,
                "setScoreRank": true,
                "setMentor": true,
                "setId": true,
                "setTags": false,
                "setStartDate": true,
                "setEndDate": true,
                "setResumeId": true,
                "setCollegeName": false
            }
        ],
        "resumeEmploymentInfo": [
            {
                "resumeId": 10072349,
                "companyName": "苏宁易购集团股份有限公司",
                "title": "java开发",
                "department": null,
                "industry": null,
                "jobNature": null,
                "category": "专业技术人员>后端开发>软件工程师",
                "toPresent": false,
                "startDate": "2024-08",
                "endDate": "2024-11",
                "salary": null,
                "jobAchieve": null,
                "jobResp": null,
                "description": null,
                "city": "南京",
                "jobLocation": null,
                "reasonForLeaving": null,
                "id": 32071154,
                "normalizedCompanyName": "苏宁易购集团股份有限公司",
                "rank": "",
                "leader": "",
                "subordinates": 0,
                "aliasName": "",
                "promote": false,
                "performance": "",
                "xhsDepartmentId": 0,
                "setToPresent": true,
                "setCity": true,
                "setCompanyName": true,
                "setDepartment": false,
                "setIndustry": false,
                "setSalary": false,
                "setJobAchieve": false,
                "setJobResp": false,
                "setDescription": false,
                "setJobLocation": false,
                "setReasonForLeaving": false,
                "setNormalizedCompanyName": true,
                "setLeader": true,
                "setSubordinates": true,
                "setPromote": false,
                "setXhsDepartmentId": true,
                "setId": true,
                "setStartDate": true,
                "setEndDate": true,
                "setResumeId": true,
                "setRank": true,
                "setCategory": true,
                "setJobNature": false,
                "setTitle": true,
                "setAliasName": true,
                "setPerformance": true
            },
            {
                "resumeId": 10072349,
                "companyName": "杭州默安科技有限公司",
                "title": "测试实习生",
                "department": null,
                "industry": null,
                "jobNature": "实习",
                "category": "专业技术人员>质量管理>测试工程师,专业技术人员>前端开发>游戏工程师",
                "toPresent": false,
                "startDate": "2024-01",
                "endDate": "2024-03",
                "salary": null,
                "jobAchieve": null,
                "jobResp": null,
                "description": null,
                "city": "杭州",
                "jobLocation": null,
                "reasonForLeaving": null,
                "id": 32071155,
                "normalizedCompanyName": "杭州默安科技有限公司",
                "rank": "",
                "leader": "",
                "subordinates": 0,
                "aliasName": "",
                "promote": false,
                "performance": "",
                "xhsDepartmentId": 0,
                "setToPresent": true,
                "setCity": true,
                "setCompanyName": true,
                "setDepartment": false,
                "setIndustry": false,
                "setSalary": false,
                "setJobAchieve": false,
                "setJobResp": false,
                "setDescription": false,
                "setJobLocation": false,
                "setReasonForLeaving": false,
                "setNormalizedCompanyName": true,
                "setLeader": true,
                "setSubordinates": true,
                "setPromote": false,
                "setXhsDepartmentId": true,
                "setId": true,
                "setStartDate": true,
                "setEndDate": true,
                "setResumeId": true,
                "setRank": true,
                "setCategory": true,
                "setJobNature": true,
                "setTitle": true,
                "setAliasName": true,
                "setPerformance": true
            }
        ],
        "resumeProjectInfo": [
            {
                "resumeId": 10072349,
                "company": null,
                "projectName": "openfriend",
                "description": "基于spring ai+postgresql+pgvector+tool calling实现的,支持多轮对话、可视化长期记忆、检索增强生成、多工具调用及基于react的长期陪伴智能体。仓库名:manus_ai_agent",
                "responsibility": "1)长期记忆机制:实现类似openclaw的可视化记忆系统,支持记忆坍缩与偏好自动更新。\n2)rag检索增强:基于pgvector 构建知识库与日记双路检索,实现查询改写、相似度检索与上下文增强,通过去重同步与advisory lock 保障索引稳定性。\n3)工具调用体系:基于spring ai@tool\n实现统一工具注册,提供搜索、抓取、文件操作、终端\n执行、资源下载、pdf 生成、记忆读写等能力4)自主规划智能体:参照openmanus实现,支持多步规划、工具调用、结果流式输出,形成完整的react 自主决策链路。",
                "startDate": "2026-02",
                "endDate": "2026-03",
                "id": 19520090,
                "role": null,
                "setCompany": false,
                "setResponsibility": true,
                "setDescription": true,
                "setId": true,
                "setStartDate": true,
                "setEndDate": true,
                "setResumeId": true,
                "setProjectName": true,
                "setRole": false
            },
            {
                "resumeId": 10072349,
                "company": null,
                "projectName": "忆存云图",
                "description": "基于spring boot+redis+cos+ai+websocket的智能图片存储与管理平台,为企业和\n个人提供图片存储、智能检索与协作服务。地址:http://118.195.165.9/,仓库名:pictureplatform",
                "responsibility": "1)权限管理:基于sa-token的kit模式实现了多账号体系的rbac权限控制;\n2)以图搜图:利用jsoup和httpclient获取百度以图搜图api,运用门面模式组合api调用获取图片列表,方便用户使用;\n3)多级缓存:利用redis+caffeine设计多级缓存架构,并通过随机过期降低缓存雪崩事件;\n4)协同编辑:基于websocket+事件驱动实现用户间图片的实时协作编辑;引入disruptor优化高并发场景下的异步任务处理,利用redis和rabbitmq实现扩展到分布式多实例的方案;\n5)智能搜索模块:搭建python agent模块,搭建milvus向量数据库,实现基于ai的智能搜索功能;将java中的核心服务包装为mcp,供python agent灵活调用;",
                "startDate": "2025-11",
                "endDate": "2026-01",
                "id": 19520091,
                "role": null,
                "setCompany": false,
                "setResponsibility": true,
                "setDescription": true,
                "setId": true,
                "setStartDate": true,
                "setEndDate": true,
                "setResumeId": true,
                "setProjectName": true,
                "setRole": false
            }
        ],
        "resumePaperInfo": [],
        "resumeAwardInfo": [],
        "resumePatentInfo": [],
        "resumeSkillInfo": [],
        "resumeTrainingInfo": [],
        "resumeHashInfo": [],
        "resumeLanguageInfo": [],
        "resumeCampusWorkInfo": [],
        "resumeCertificateInfo": [],
        "previewResumeUrl": "https://ep-ehr-s1.xhscdn.com/recruit/1040g28g31u7mhpb7gu0000000000ibbbgi5pa88.pdf?sign=b9af8fa18d6b35d1bcde6cd799e560ca&t=69c7fc28",
        "internEntryDateDesc": "1周内",
        "internshipTimeDesc": "6个月以上",
        "internDayPerWeek": 5,
        "internEntryDate": "one_week",
        "internshipTime": "more_than_six_months"
    },
    "tags": null,
    "order": 0,
    "interviewStatusDesc": "简历初筛(进行中)",
    "orderStr": null
}'