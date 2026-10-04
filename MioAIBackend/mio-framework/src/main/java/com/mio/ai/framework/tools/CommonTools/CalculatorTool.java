package com.mio.ai.framework.tools.CommonTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/10/4
 * @description: 精确计算器：手写递归下降解析（不引脚本引擎，JDK21 无 Nashorn），
 * 支持 + - * / % ^ 括号与一元负号，避免大数/多步运算时模型心算出错。
 */
@Component
public class CalculatorTool {

    @Tool(description = "精确计算算术表达式（支持 + - * / % ^ 与括号）。涉及金额、日期差、大数或精度敏感的计算时使用，不要心算")
    public String calculate(@ToolParam(description = "算术表达式，例如 (12500*0.85+300)/12") String expression) {
        try {
            double result = new Parser(expression == null ? "" : expression).parse();
            if (Double.isInfinite(result) || Double.isNaN(result)) {
                return "计算结果不是有效数字（可能除以了 0）";
            }
            return format(result);
        } catch (ArithmeticException e) {
            return "计算错误: " + e.getMessage();
        } catch (Exception e) {
            return "无法解析的表达式（仅支持数字与 + - * / % ^ 括号）: " + e.getMessage();
        }
    }

    /** 去掉无意义的小数尾巴：12.0 → 12，12.300000000000001 保留原样 */
    static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)
                && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** 递归下降解析器：expr → term → power → unary → primary */
    static final class Parser {
        private final String src;
        private int pos = -1;
        private int ch;

        Parser(String src) {
            this.src = src;
        }

        double parse() {
            advance();
            skipWhitespace();
            double value = parseExpr();
            skipWhitespace();
            if (ch != -1) {
                throw new IllegalArgumentException("位置 " + pos + " 附近存在无法解析的字符");
            }
            return value;
        }

        private double parseExpr() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (ch == '+') {
                    advance();
                    value += parseTerm();
                } else if (ch == '-') {
                    advance();
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parsePower();
            while (true) {
                skipWhitespace();
                if (ch == '*') {
                    advance();
                    value *= parsePower();
                } else if (ch == '/') {
                    advance();
                    double divisor = parsePower();
                    if (divisor == 0) {
                        throw new ArithmeticException("除数为 0");
                    }
                    value /= divisor;
                } else if (ch == '%') {
                    advance();
                    double divisor = parsePower();
                    if (divisor == 0) {
                        throw new ArithmeticException("取模的除数为 0");
                    }
                    value %= divisor;
                } else {
                    return value;
                }
            }
        }

        /** 幂运算右结合：2^3^2 = 2^9 */
        private double parsePower() {
            double base = parseUnary();
            skipWhitespace();
            if (ch == '^') {
                advance();
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() {
            skipWhitespace();
            if (ch == '-') {
                advance();
                return -parseUnary();
            }
            if (ch == '+') {
                advance();
                return parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            skipWhitespace();
            if (ch == '(') {
                advance();
                double value = parseExpr();
                skipWhitespace();
                if (ch != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                advance();
                return value;
            }
            if (ch >= '0' && ch <= '9' || ch == '.') {
                int start = pos;
                while (ch >= '0' && ch <= '9' || ch == '.') {
                    advance();
                }
                String number = src.substring(start, pos);
                try {
                    return Double.parseDouble(number);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("非法数字 '" + number + "'");
                }
            }
            throw new IllegalArgumentException(ch == -1 ? "表达式意外结束" : "位置 " + pos + " 缺少数字或括号");
        }

        private void skipWhitespace() {
            while (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                advance();
            }
        }

        private void advance() {
            ch = (++pos < src.length()) ? src.charAt(pos) : -1;
        }
    }
}
