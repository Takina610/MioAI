-- 技能"是否公开"改为全员可设置：GitHub 来源统一默认公开（广场陈列条件收紧为 is_public=1）
-- 存量 GitHub 来源技能回填 is_public=1，保持其在广场的可见性不变
UPDATE skill
SET is_public = 1
WHERE repo_owner IS NOT NULL
  AND repo_owner != ''
  AND is_public = 0
  AND is_deleted = 0;
