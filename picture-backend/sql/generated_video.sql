-- 生成视频表（结构与 picture 对齐字段风格，独立存储；请在 yu_picture 库执行）
USE yu_picture;

CREATE TABLE IF NOT EXISTS generated_video
(
    id             BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    url            VARCHAR(512)                       NOT NULL COMMENT '视频 COS url',
    name           VARCHAR(128)                       NOT NULL COMMENT '名称',
    introduction   VARCHAR(512)                       NULL COMMENT '简介',
    category       VARCHAR(64)                        NULL COMMENT '分类',
    tags           VARCHAR(512)                       NULL COMMENT '标签 JSON',
    videoSize      BIGINT                             NULL COMMENT '视频体积 字节',
    videoFormat    VARCHAR(32)                        NULL COMMENT '格式 mp4 等',
    thumbnailUrl   VARCHAR(512)                       NULL COMMENT '封面/缩略图',
    userId         BIGINT                             NOT NULL COMMENT '创建用户 id',
    spaceId        BIGINT                             NOT NULL COMMENT '空间 id',
    reviewStatus   INT      DEFAULT 1                 NOT NULL COMMENT '审核 0待审 1通过 2拒绝',
    reviewMessage  VARCHAR(512)                       NULL,
    reviewerId     BIGINT                             NULL,
    reviewTime     DATETIME                           NULL,
    arkTaskId      VARCHAR(128)                       NULL COMMENT '方舟任务 id',
    conversationId BIGINT                           NULL COMMENT 'Agent 对话 id',
    createTime     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    editTime       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updateTime     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    isDelete       TINYINT  DEFAULT 0                 NOT NULL,
    INDEX idx_userId (userId),
    INDEX idx_spaceId (spaceId)
) COMMENT 'Agent 生成视频' COLLATE = utf8mb4_unicode_ci;
