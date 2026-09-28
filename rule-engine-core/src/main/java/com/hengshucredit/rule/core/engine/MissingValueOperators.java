package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.runtime.Nothing;
import com.alibaba.qlexpress4.runtime.Value;
import com.alibaba.qlexpress4.runtime.operator.CustomBinaryOperator;
import com.hengshucredit.rule.core.util.MissingValueSemantics;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** QLExpress operators that make generic comparisons safe for missing values. */
final class MissingValueOperators {

    private MissingValueOperators() {
    }

    static void register(Express4Runner runner) {
        runner.replaceDefaultOperator(">", ordered(Comparison.GREATER));
        runner.replaceDefaultOperator(">=", ordered(Comparison.GREATER_EQUAL));
        runner.replaceDefaultOperator("<", ordered(Comparison.LESS));
        runner.replaceDefaultOperator("<=", ordered(Comparison.LESS_EQUAL));
        runner.replaceDefaultOperator("==", equality(false));
        runner.replaceDefaultOperator("!=", equality(true));
        runner.replaceDefaultOperator("<>", equality(true));
        runner.replaceDefaultOperator("in", membership(false));
        runner.replaceDefaultOperator("not_in", membership(true));
    }

    private static CustomBinaryOperator ordered(Comparison comparison) {
        return (left, right) -> {
            Object l = value(left);
            Object r = value(right);
            if (MissingValueSemantics.isMissing(l) || MissingValueSemantics.isMissing(r)) {
                return false;
            }
            int result = compare(l, r);
            return switch (comparison) {
                case GREATER -> result > 0;
                case GREATER_EQUAL -> result >= 0;
                case LESS -> result < 0;
                case LESS_EQUAL -> result <= 0;
            };
        };
    }

    private static CustomBinaryOperator equality(boolean negate) {
        return (left, right) -> {
            Object l = value(left);
            Object r = value(right);
            boolean equal;
            if (nullLike(l) || nullLike(r)) {
                equal = nullLike(l) && nullLike(r);
            } else {
                equal = valueEquals(l, r);
            }
            return negate ? !equal : equal;
        };
    }

    private static CustomBinaryOperator membership(boolean negate) {
        return (left, right) -> {
            Object l = value(left);
            Object r = value(right);
            if (MissingValueSemantics.isMissing(l) || MissingValueSemantics.isMissing(r)) return false;
            boolean matched = false;
            if (r instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (valueEquals(l, item)) {
                        matched = true;
                        break;
                    }
                }
            } else if (r != null && r.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(r);
                for (int i = 0; i < length; i++) {
                    if (valueEquals(l, java.lang.reflect.Array.get(r, i))) {
                        matched = true;
                        break;
                    }
                }
            }
            return negate ? (matched ? false : true) : matched;
        };
    }

    private static Object value(Value value) {
        Object result = value == null ? null : value.get();
        if (result instanceof Nothing) return null;
        return MissingValueSemantics.normalizeScalar(result);
    }

    private static boolean nullLike(Object value) {
        return value == null || value instanceof Nothing;
    }

    private static boolean valueEquals(Object left, Object right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        BigDecimal l = number(left);
        BigDecimal r = number(right);
        if (l != null && r != null) return l.compareTo(r) == 0;
        return Objects.equals(left, right);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compare(Object left, Object right) {
        BigDecimal l = number(left);
        BigDecimal r = number(right);
        if (l != null && r != null) return l.compareTo(r);
        if (left instanceof CharSequence && right instanceof CharSequence) {
            return left.toString().compareTo(right.toString());
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        throw new IllegalArgumentException("值不支持比较: " + left + " 与 " + right);
    }

    private static BigDecimal number(Object value) {
        if (!(value instanceof Number)) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum Comparison {
        GREATER, GREATER_EQUAL, LESS, LESS_EQUAL
    }
}
