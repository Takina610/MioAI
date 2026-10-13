-- 2026-10-13 群聊智能体（agent.type=2：多个成员智能体的集合，按任务委派成员执行）：
-- ① 新建 agent_group_member 关联表（群 ↔ 成员，成员限定用户自己的自定义智能体 type=1）；
-- ② agent.type 枚举扩至 2-群聊智能体（仅注释，存储本身是 tinyint 无需变更）。
-- 执行方式（防中文注释乱码）：
-- mysql --default-character-set=utf8mb4 -uroot -p mio_ai < this-file.sql

CREATE TABLE `agent_group_member`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_agent_id` bigint NOT NULL COMMENT '群聊智能体ID（agent.type=2）',
  `member_agent_id` bigint NOT NULL COMMENT '成员智能体ID（agent.type=1）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_group_member`(`group_agent_id` ASC, `member_agent_id` ASC) USING BTREE,
  INDEX `idx_member_agent_id`(`member_agent_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群聊智能体成员表' ROW_FORMAT = Dynamic;

ALTER TABLE `agent`
  MODIFY COLUMN `type` tinyint NULL DEFAULT 0 COMMENT '类型:0-系统内置超级智能体,1-自定义智能体,2-群聊智能体';
