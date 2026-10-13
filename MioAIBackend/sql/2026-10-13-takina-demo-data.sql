-- 2026-10-13 Takina(user_id=1) 测试数据：8 个自定义智能体 + 4 个不同规模的群聊
-- （2/3/4/6 成员各一，用于验证堆叠群头像 2~4 全显、5+ 显示 3+省略号）
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p mio_ai < this-file.sql

SET @uid = 1;

INSERT INTO agent (user_id, name, description, icon, type, system_prompt, status, is_public) VALUES
(@uid, '翻译专家', '精通中英日三语互译，译文自然地道，保留原文语气', '{"shape":"hex","fill":{"kind":"palette","id":"cyan"}}', 1, '你是一位资深翻译，精通中英日三语互译。收到文本后直接给出自然地道的译文，必要时附一句关键用词说明；不要解释翻译理论。', 1, 0),
(@uid, '代码审查官', '熟悉 Java/Python/前端代码，找缺陷并给修改建议', '{"shape":"squircle","fill":{"kind":"palette","id":"orange"}}', 1, '你是一位资深代码审查工程师。审查代码时先指出缺陷与风险（按严重程度排序），再给出具体修改建议与示例代码；没有问题的部分不重复夸赞。', 1, 0),
(@uid, '数据分析师', '数据清洗、统计分析、图表呈现与业务洞察', '{"shape":"gem","fill":{"kind":"palette","id":"violet"}}', 1, '你是一位数据分析师。分析问题时先明确口径与假设，再给结论；能用数字说话就用数字，重要结论标注数据来源与不确定性。', 1, 0),
(@uid, '文案高手', '营销文案、社交媒体运营与品牌故事写作', '{"shape":"cloud","fill":{"kind":"palette","id":"magenta"}}', 1, '你是一位创意文案。写作风格干净有记忆点，先给一句主文案，再给 2-3 条备选；避免堆砌形容词与空话。', 1, 0),
(@uid, '旅行规划师', '行程定制、景点推荐、预算与交通安排', '{"shape":"leaf","fill":{"kind":"palette","id":"green"}}', 1, '你是一位旅行规划师。规划行程时按天给出可执行的安排（景点+交通+用餐建议），标注预算区间与季节提示；不推荐明显坑人的项目。', 1, 0),
(@uid, '健康顾问', '作息、饮食、运动方面的健康生活习惯建议', '{"shape":"blob","fill":{"kind":"palette","id":"blue"}}', 1, '你是一位健康生活顾问，只提供作息、饮食、运动等生活方式建议，不进行诊断与用药指导；涉及疾病症状时建议就医。', 1, 0),
(@uid, '面试教练', '模拟面试、简历点评与答题框架辅导', '{"shape":"capsule","fill":{"kind":"palette","id":"yellow"}}', 1, '你是一位面试教练。点评回答时先说亮点，再指出最大的问题并给改进后的示范回答；按 STAR 框架组织建议。', 1, 0),
(@uid, '美食推荐官', '菜系推荐、家常菜谱与用餐搭配', '{"shape":"teardrop","fill":{"kind":"palette","id":"red"}}', 1, '你是一位美食推荐官。推荐时说明口味特点与适合场景，给家常做法时列食材与关键步骤，不堆砌菜名。', 1, 0);

INSERT INTO agent (user_id, name, description, type, system_prompt, status, is_public) VALUES
(@uid, '双语翻译组', '中英日互译的双人小组', 2, NULL, 1, 0),
(@uid, '创意工坊', '代码、数据、文案三人协作', 2, NULL, 1, 0),
(@uid, '出行规划团', '旅行、翻译、美食、健康四人出行智囊', 2, NULL, 1, 0),
(@uid, '全能工作室', '六位专家组成的全能群聊', 2, NULL, 1, 0);

INSERT INTO agent_group_member (group_agent_id, member_agent_id)
SELECT g.id, m.id FROM agent g JOIN agent m
  ON m.user_id = @uid AND m.type = 1
  AND m.name IN ('翻译专家', '文案高手')
WHERE g.user_id = @uid AND g.type = 2 AND g.name = '双语翻译组';

INSERT INTO agent_group_member (group_agent_id, member_agent_id)
SELECT g.id, m.id FROM agent g JOIN agent m
  ON m.user_id = @uid AND m.type = 1
  AND m.name IN ('代码审查官', '数据分析师', '文案高手')
WHERE g.user_id = @uid AND g.type = 2 AND g.name = '创意工坊';

INSERT INTO agent_group_member (group_agent_id, member_agent_id)
SELECT g.id, m.id FROM agent g JOIN agent m
  ON m.user_id = @uid AND m.type = 1
  AND m.name IN ('旅行规划师', '翻译专家', '美食推荐官', '健康顾问')
WHERE g.user_id = @uid AND g.type = 2 AND g.name = '出行规划团';

INSERT INTO agent_group_member (group_agent_id, member_agent_id)
SELECT g.id, m.id FROM agent g JOIN agent m
  ON m.user_id = @uid AND m.type = 1
  AND m.name IN ('翻译专家', '代码审查官', '数据分析师', '文案高手', '旅行规划师', '面试教练')
WHERE g.user_id = @uid AND g.type = 2 AND g.name = '全能工作室';
