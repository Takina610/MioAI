-- 2026-10-12 智能体配置升级：
-- 1) agent 表新增开场白/推荐问题/温度配置列
-- 2) agent_user_config：内置智能体（MioBot）的用户级个性化配置（每人一套独立配置）
-- 3) agent_version：发布版本快照（可查看历史配置并回滚）
-- 执行方式（防中文注释乱码）：
-- mysql --default-character-set=utf8mb4 -uroot -p mio_ai < this-file.sql

ALTER TABLE `agent`
  ADD COLUMN `opening_message` varchar(1000) NULL COMMENT '开场白（新会话首屏展示）' AFTER `system_prompt`,
  ADD COLUMN `opening_questions` varchar(2000) NULL COMMENT '推荐问题JSON数组' AFTER `opening_message`,
  ADD COLUMN `temperature` double NULL COMMENT '采样温度（NULL=引擎默认）' AFTER `opening_questions`;

-- MioBot 内置默认开场白（存量行补默认值）
UPDATE `agent`
SET `opening_message` = '你好，我是 MioBot。我可以联网搜索、抓取网页、读写文件、跑终端命令，帮你把事情一步步做完。有什么可以帮你的？'
WHERE `id` = 1 AND `opening_message` IS NULL;

CREATE TABLE `agent_user_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_id` bigint NOT NULL COMMENT '内置智能体ID',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '人设提示词覆盖',
  `opening_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '开场白覆盖',
  `opening_questions` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '推荐问题JSON数组覆盖',
  `temperature` double NULL COMMENT '采样温度覆盖（NULL=跟随内置默认）',
  `icon` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '图标配置覆盖JSON',
  `is_deleted` tinyint NULL DEFAULT 0 COMMENT '是否删除:0-未删除,1-已删除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_agent`(`user_id` ASC, `agent_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '内置智能体的用户级个性化配置' ROW_FORMAT = Dynamic;

CREATE TABLE `agent_version`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '版本号（v1.0格式，每次发布/回滚递增）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '名称快照',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '描述快照',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '头像快照',
  `icon` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '图标快照JSON',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统提示词快照',
  `opening_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '开场白快照',
  `opening_questions` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '推荐问题快照JSON数组',
  `temperature` double NULL COMMENT '采样温度快照',
  `bindings_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '资源绑定快照JSON（知识库/MCP/技能）',
  `rollback_from` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '回滚来源版本号（回滚产生的版本才有）',
  `user_id` bigint NULL COMMENT '发布人ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent`(`agent_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '智能体发布版本快照' ROW_FORMAT = Dynamic;
