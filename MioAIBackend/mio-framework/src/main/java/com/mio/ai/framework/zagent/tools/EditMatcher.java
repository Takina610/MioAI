package com.mio.ai.framework.zagent.tools;

import java.util.List;

/**
 * 编辑匹配（zcode edit-matchers 的对位移植）：
 * 精确匹配优先，失败后退到缩进灵活匹配（剥离各行公共前导空白后比对），
 * 保持替换文本的原始缩进风格。
 */
final class EditMatcher {

    record Match(int startLine, int endLine) {
    }

    private EditMatcher() {
    }

    /** 在行列表中定位目标文本；返回起始行与结束行（闭区间），未找到返回 null */
    static Match find(List<String> lines, String oldString, boolean ignoreIndent) {
        List<String> target = oldString.lines().toList();
        if (target.isEmpty() || lines.isEmpty()) {
            return null;
        }
        for (int i = 0; i + target.size() <= lines.size(); i++) {
            if (matchesAt(lines, i, target, ignoreIndent)) {
                return new Match(i, i + target.size() - 1);
            }
        }
        return null;
    }

    private static boolean matchesAt(List<String> lines, int start, List<String> target, boolean ignoreIndent) {
        for (int j = 0; j < target.size(); j++) {
            String line = lines.get(start + j);
            String want = target.get(j);
            if (ignoreIndent) {
                if (!stripIndent(line).equals(stripIndent(want))) {
                    return false;
                }
            } else if (!line.equals(want)) {
                return false;
            }
        }
        return true;
    }

    private static String stripIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(i);
    }

    /** 统计出现次数（与 find 同一匹配策略） */
    static int count(List<String> lines, String oldString, boolean ignoreIndent) {
        List<String> target = oldString.lines().toList();
        if (target.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i + target.size() <= lines.size(); i++) {
            if (matchesAt(lines, i, target, ignoreIndent)) {
                count++;
            }
        }
        return count;
    }
}
