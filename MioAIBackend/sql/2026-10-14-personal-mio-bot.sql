-- 每个用户一份专属 MioBot（agent.type=3）：为存量用户补建（新用户由注册事件自动创建）
-- 初始配置复制内置 MioBot（id=1），用户保存过的个性化（人设/图标）带入副本
INSERT INTO agent (user_id, name, description, icon, type, system_prompt, status, is_public, usage_count, version, is_deleted, create_time, update_time)
SELECT u.id, b.name, b.description,
       COALESCE(NULLIF(c.icon, ''), b.icon), 3,
       COALESCE(NULLIF(c.system_prompt, ''), b.system_prompt),
       1, 0, 0, b.version, 0, NOW(), NOW()
FROM user u
JOIN agent b ON b.id = 1
LEFT JOIN agent_user_config c ON c.user_id = u.id AND c.agent_id = 1 AND c.is_deleted = 0
WHERE u.is_delete = 0
  AND NOT EXISTS (
    SELECT 1 FROM agent a
    WHERE a.user_id = u.id AND a.type = 3 AND a.is_deleted = 0
  );
