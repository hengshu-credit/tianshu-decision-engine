package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import com.alibaba.qlexpress4.security.StrategyWhiteList;

import java.lang.reflect.Member;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public final class QLExpressScriptSecurity {

    private QLExpressScriptSecurity() {
    }

    public static QLSecurityStrategy standardFunctionWhitelist() {
        Set<Member> members = new HashSet<>();
        addStringMembers(members);
        addNumberMembers(members);
        addDateMembers(members);
        return new StrategyWhiteList(members);
    }

    private static void addStringMembers(Set<Member> members) {
        addMethod(members, String.class, "trim");
        addMethod(members, String.class, "length");
        addMethod(members, String.class, "substring", int.class);
        addMethod(members, String.class, "substring", int.class, int.class);
        addMethod(members, String.class, "matches", String.class);
        addMethod(members, String.class, "equals", Object.class);
    }

    private static void addNumberMembers(Set<Member> members) {
        addMethod(members, Number.class, "intValue");
        addMethod(members, Integer.class, "intValue");
        addMethod(members, Long.class, "intValue");
        addMethod(members, Double.class, "intValue");
        addMethod(members, Float.class, "intValue");
        addMethod(members, BigDecimal.class, "intValue");
        addMethod(members, Integer.class, "parseInt", String.class);
    }

    private static void addDateMembers(Set<Member> members) {
        addConstructor(members, Date.class);
        addConstructor(members, Date.class, int.class, int.class, int.class);
        addMethod(members, Date.class, "getYear");
        addMethod(members, Date.class, "getMonth");
        addMethod(members, Date.class, "getDate");
        addConstructor(members, SimpleDateFormat.class, String.class);
        addMethod(members, SimpleDateFormat.class, "setLenient", boolean.class);
        addMethod(members, SimpleDateFormat.class, "parse", String.class);
    }

    private static void addMethod(Set<Member> members, Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            members.add(type.getMethod(name, parameterTypes));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing QLExpress whitelist method: " + type.getName() + "#" + name, e);
        }
    }

    private static void addConstructor(Set<Member> members, Class<?> type, Class<?>... parameterTypes) {
        try {
            members.add(type.getConstructor(parameterTypes));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing QLExpress whitelist constructor: " + type.getName(), e);
        }
    }
}
