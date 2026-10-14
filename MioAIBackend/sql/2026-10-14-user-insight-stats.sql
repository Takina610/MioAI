-- 2026-10-14 用户数据洞察统计升级
-- ① agent_usage_log 补记模型名（模型维度趋势/模型用量饼图的数据来源，存量行为 NULL 归入"未知模型"）
-- ② user_stats_daily：用户×日期粒度的用量累计表（贡献图/连续天数/峰值/聊天时长的数据来源）
-- ③ skill_usage_log：技能使用日志（模型读取 skills/<id>/ 文件即视为使用该技能，每轮每技能一条）
-- ④ user_activity_log：用户执行日志（写操作 AOP 落库 + 登录/注册事件落库）
--
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p mio_ai < 2026-10-14-user-insight-stats.sql

-- ① 用量日志补模型列
ALTER TABLE agent_usage_log
    ADD COLUMN model VARCHAR(128) NULL DEFAULT NULL COMMENT '模型名（本轮会话使用的模型标识）' AFTER conversation_id;

-- ② 用户每日用量累计
CREATE TABLE IF NOT EXISTS user_stats_daily (
    id BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输入Token累计',
    output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '输出Token累计',
    call_count INT NOT NULL DEFAULT 0 COMMENT '对话轮次累计',
    chat_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '聊天耗时累计（毫秒）',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_date (user_id, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户每日用量累计表';

-- ③ 技能使用日志
CREATE TABLE IF NOT EXISTS skill_usage_log (
    id BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    agent_id BIGINT NULL DEFAULT NULL COMMENT '智能体ID（群聊成员使用时为成员智能体ID）',
    conversation_id BIGINT NULL DEFAULT NULL COMMENT '会话ID（数字型会话才有）',
    skill_id BIGINT NOT NULL COMMENT '技能ID',
    skill_name VARCHAR(128) NULL DEFAULT NULL COMMENT '技能名称（冗余，防技能删除后无法展示）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_time (user_id, create_time),
    KEY idx_skill (skill_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='技能使用日志表';

-- ④ 用户执行日志
CREATE TABLE IF NOT EXISTS user_activity_log (
    id BIGINT AUTO_INCREMENT COMMENT '主键' PRIMARY KEY,
    user_id BIGINT NULL DEFAULT NULL COMMENT '用户ID（未登录的写操作为 NULL）',
    module VARCHAR(32) NOT NULL COMMENT '业务模块（agent/knowledge/mcp/skill/model/chat/auth 等）',
    action VARCHAR(64) NOT NULL COMMENT '执行动作（方法名或 login/register 等语义动作）',
    http_method VARCHAR(16) NULL DEFAULT NULL COMMENT 'HTTP 方法',
    path VARCHAR(256) NULL DEFAULT NULL COMMENT '请求路径',
    summary VARCHAR(512) NULL DEFAULT NULL COMMENT '入参摘要（JSON 截断）',
    success TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功（1-成功 0-失败）',
    error_msg VARCHAR(512) NULL DEFAULT NULL COMMENT '失败摘要',
    cost_ms BIGINT NULL DEFAULT NULL COMMENT '执行耗时（毫秒）',
    ip VARCHAR(64) NULL DEFAULT NULL COMMENT '客户端 IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_time (user_id, create_time),
    KEY idx_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户执行日志表';
