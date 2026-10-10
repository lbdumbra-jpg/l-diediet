package com.lumbra.ldiediet.math;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ExpressionParser {

    public interface Expression {
        double eval(Map<String, Double> vars);
    }

    public static Expression parse(String str) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException("Expression must not be blank");
        }
        return new Parser(str).parse();
    }

     
    public static Set<String> variables(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Expression must not be blank");
        var parser = new Parser(source);
        parser.parse();
        return Set.copyOf(parser.variables);
    }

     
    public static Expression parseForDiagnostics(String source) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Expression must not be blank");
        return new Parser(source, true).parse();
    }

    private static class Parser {
        int pos = -1, ch;
        String str;
        final Set<String> variables = new HashSet<>();
        final boolean checked;

        Parser(String str) {
            this(str, false);
        }

        Parser(String str, boolean checked) {
            this.str = str;
            this.checked = checked;
        }

        Expression checked(Expression expression) {
            if (!checked) return expression;
            return vars -> {
                double value = expression.eval(vars);
                if (!Double.isFinite(value)) throw new ArithmeticException("non-finite intermediate result (division by zero/domain error)");
                return value;
            };
        }

        void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        Expression parse() {
            nextChar();
            Expression x = parseExpression();
            if (ch != -1) throw error("Unexpected character: " + (char) ch);
            return x;
        }

        Expression parseExpression() {
            Expression x = parseTerm();
            for (;;) {
                if      (eat('+')) { Expression a = x, b = parseTerm(); x = vars -> a.eval(vars) + b.eval(vars); }
                else if (eat('-')) { Expression a = x, b = parseTerm(); x = vars -> a.eval(vars) - b.eval(vars); }
                else return checked(x);
            }
        }

        Expression parseTerm() {
            Expression x = parseFactor();
            for (;;) {
                if      (eat('*')) { Expression a = x, b = parseFactor(); x = vars -> a.eval(vars) * b.eval(vars); }
                else if (eat('/')) { Expression a = x, b = parseFactor(); x = vars -> {
                    double numerator = a.eval(vars), denominator = b.eval(vars);
                    if (checked && denominator == 0) throw new ArithmeticException("division by zero");
                    return numerator / denominator;
                }; }
                else return checked(x);
            }
        }

        Expression parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) { Expression a = parseFactor(); return vars -> -a.eval(vars); }

            Expression x;
            int startPos = this.pos;
            if (eat('(')) {
                x = parseExpression();
                require(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                boolean hasExponent = false;
                while ((ch >= '0' && ch <= '9') || ch == '.' || (!hasExponent && (ch == 'e' || ch == 'E'))
                        || (hasExponent && (ch == '+' || ch == '-') && pos > startPos
                        && (str.charAt(pos - 1) == 'e' || str.charAt(pos - 1) == 'E'))) {
                    if (ch == 'e' || ch == 'E') hasExponent = true;
                    nextChar();
                }
                double val;
                try {
                    val = Double.parseDouble(str.substring(startPos, this.pos));
                } catch (NumberFormatException e) {
                    throw error("Invalid number");
                }
                x = vars -> val;
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_') {
                while ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_' || (ch >= '0' && ch <= '9')) nextChar();
                String func = str.substring(startPos, this.pos);
                if (eat('(')) {
                    Expression arg1 = parseExpression();
                    Expression arg2 = null;
                    if (eat(',')) {
                        arg2 = parseExpression();
                    }
                    require(')');

                    if (func.equals("sqrt") && arg2 == null) { x = vars -> Math.sqrt(arg1.eval(vars)); }
                    else if (func.equals("abs") && arg2 == null) { x = vars -> Math.abs(arg1.eval(vars)); }
                    else if ((func.equals("ln") || func.equals("log")) && arg2 == null) { x = vars -> Math.log(arg1.eval(vars)); }
                    else if (func.equals("max") && arg2 != null) { Expression a = arg1, b = arg2; x = vars -> Math.max(a.eval(vars), b.eval(vars)); }
                    else if (func.equals("min") && arg2 != null) { Expression a = arg1, b = arg2; x = vars -> Math.min(a.eval(vars), b.eval(vars)); }
                    else if (func.equals("pow") && arg2 != null) { Expression a = arg1, b = arg2; x = vars -> Math.pow(a.eval(vars), b.eval(vars)); }
                    else throw error("Unknown function or invalid arity: " + func);
                } else {
                    if (func.equalsIgnoreCase("pi")) {
                        x = vars -> Math.PI;
                    } else if (func.equalsIgnoreCase("e")) {
                        x = vars -> Math.E;
                    } else {
                        variables.add(func);
                        x = vars -> {
                            Double value = vars.get(func);
                            if (value == null) throw new IllegalArgumentException("Missing variable: " + func);
                            return value;
                        };
                    }
                }
            } else {
                throw error("Unexpected character: " + (char) ch);
            }

            if (eat('^')) { Expression a = x, b = parseFactor(); x = vars -> Math.pow(a.eval(vars), b.eval(vars)); }

            return checked(x);
        }

        void require(int expected) {
            if (!eat(expected)) throw error("Expected '" + (char) expected + "'");
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + pos + " in '" + str + "'");
        }
    }
}
