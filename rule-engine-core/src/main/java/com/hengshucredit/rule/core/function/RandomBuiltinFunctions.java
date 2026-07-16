package com.hengshucredit.rule.core.function;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

/** 普通业务抽样使用的随机整数和随机小数函数，不用于安全随机场景。 */
public class RandomBuiltinFunctions {

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    public long randomInt(Object... arguments) {
        Object[] args = normalizeArguments("randomInt", arguments);
        long lower = requireLong("randomInt", "下界", args[0]);
        long upper = requireLong("randomInt", "上界", args[1]);
        boolean includeLower = requireBoolean("randomInt", args[2]);
        boolean includeUpper = requireBoolean("randomInt", args[3]);
        if (lower > upper) {
            throw new IllegalArgumentException("randomInt 下界不能大于上界");
        }
        if (!includeLower) {
            if (lower == Long.MAX_VALUE) throw noInteger();
            lower++;
        }
        if (!includeUpper) {
            if (upper == Long.MIN_VALUE) throw noInteger();
            upper--;
        }
        if (lower > upper) throw noInteger();
        if (lower == upper) return lower;
        if (upper != Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong(lower, upper + 1L);
        }
        BigInteger origin = BigInteger.valueOf(lower);
        BigInteger range = LONG_MAX.subtract(origin).add(BigInteger.ONE);
        BigInteger offset;
        do {
            offset = new BigInteger(range.bitLength(), ThreadLocalRandom.current());
        } while (offset.compareTo(range) >= 0);
        return origin.add(offset).longValue();
    }

    public long randomIntForManagement(double lower, double upper,
                                       boolean includeLower, boolean includeUpper) {
        return randomInt(new Object[]{lower, upper, includeLower, includeUpper});
    }

    public double randomDecimal(Object... arguments) {
        Object[] args = normalizeArguments("randomDecimal", arguments);
        double lower = requireFiniteDouble("randomDecimal", "下界", args[0]);
        double upper = requireFiniteDouble("randomDecimal", "上界", args[1]);
        boolean includeLower = requireBoolean("randomDecimal", args[2]);
        boolean includeUpper = requireBoolean("randomDecimal", args[3]);
        if (lower > upper) {
            throw new IllegalArgumentException("randomDecimal 下界不能大于上界");
        }
        if (lower == upper) {
            if (includeLower && includeUpper) return lower;
            throw new IllegalArgumentException("randomDecimal 配置形成空区间");
        }
        double effectiveLower = includeLower ? lower : Math.nextUp(lower);
        double effectiveUpper = includeUpper ? upper : Math.nextDown(upper);
        if (Double.compare(effectiveLower, effectiveUpper) > 0) {
            throw new IllegalArgumentException("randomDecimal 配置形成空区间");
        }
        if (Double.compare(effectiveLower, effectiveUpper) == 0) return effectiveLower;

        double unit = ThreadLocalRandom.current().nextDouble();
        double value;
        if (effectiveLower < 0D && effectiveUpper > 0D) {
            value = effectiveLower * (1D - unit) + effectiveUpper * unit;
        } else {
            value = effectiveLower + (effectiveUpper - effectiveLower) * unit;
        }
        if (Double.isNaN(value) || value < effectiveLower) return effectiveLower;
        if (Double.isInfinite(value) || value > effectiveUpper) return effectiveUpper;
        return value;
    }

    public double randomDecimalForManagement(double lower, double upper,
                                             boolean includeLower, boolean includeUpper) {
        return randomDecimal(new Object[]{lower, upper, includeLower, includeUpper});
    }

    private static Object[] normalizeArguments(String functionName, Object[] arguments) {
        int size = arguments == null ? 0 : arguments.length;
        if (size == 0) return new Object[]{0L, 1L, true, true};
        if (size == 2) return new Object[]{arguments[0], arguments[1], true, true};
        if (size == 4) return arguments;
        throw new IllegalArgumentException(functionName + " 参数数量必须为 0、2 或 4");
    }

    private static long requireLong(String functionName, String label, Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(functionName + " " + label + "必须是数字");
        }
        BigDecimal decimal;
        try {
            if (value instanceof BigDecimal) {
                decimal = (BigDecimal) value;
            } else if (value instanceof BigInteger) {
                decimal = new BigDecimal((BigInteger) value);
            } else {
                decimal = new BigDecimal(value.toString());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(functionName + " " + label + "必须是整数");
        }
        BigInteger integer;
        try {
            integer = decimal.toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(functionName + " " + label + "必须是整数");
        }
        if (integer.compareTo(LONG_MIN) < 0 || integer.compareTo(LONG_MAX) > 0) {
            throw new IllegalArgumentException(functionName + " " + label + "超出 Long 范围");
        }
        return integer.longValue();
    }

    private static double requireFiniteDouble(String functionName, String label, Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(functionName + " " + label + "必须是数字");
        }
        double number = ((Number) value).doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new IllegalArgumentException(functionName + " " + label + "必须是有限数字");
        }
        return number;
    }

    private static boolean requireBoolean(String functionName, Object value) {
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(functionName + " 开闭参数必须是布尔值");
        }
        return (Boolean) value;
    }

    private static IllegalArgumentException noInteger() {
        return new IllegalArgumentException("randomInt 区间内不存在可选整数");
    }
}
