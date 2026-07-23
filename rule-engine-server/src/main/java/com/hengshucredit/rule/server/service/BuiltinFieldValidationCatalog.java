package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleFieldValidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BuiltinFieldValidationCatalog {

    private static final Set<String> RESERVED_CODES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "builtin_required",
                    "builtin_digits",
                    "builtin_digits_min_16",
                    "builtin_digits_15_to_18",
                    "builtin_chinese",
                    "builtin_alphanumeric",
                    "builtin_email",
                    "builtin_domain",
                    "builtin_mobile",
                    "builtin_id_card",
                    "builtin_ip_address"
            )));

    private BuiltinFieldValidationCatalog() {
    }

    static List<RuleFieldValidation> definitions() {
        List<RuleFieldValidation> definitions = new ArrayList<>();
        definitions.add(rule("builtin_required", "必填", "REQUIRED", null,
                "该字段不能为空", "校验字段必须提供有效值。"));
        definitions.add(regex("builtin_digits", "数字", "^[0-9]*$",
                "请输入数字", "只允许输入数字。"));
        definitions.add(regex("builtin_digits_min_16", "至少16位数字", "^\\d{16,}$",
                "请输入至少16位数字", "只允许输入不少于16位的数字。"));
        definitions.add(regex("builtin_digits_15_to_18", "15-18位数字", "^\\d{15,18}$",
                "请输入15至18位数字", "只允许输入15至18位数字。"));
        definitions.add(regex("builtin_chinese", "汉字", "^[\\u4e00-\\u9fa5]{0,}$",
                "请输入汉字", "只允许输入汉字。"));
        definitions.add(regex("builtin_alphanumeric", "英文和数字", "^[a-zA-Z0-9]+$",
                "请输入英文或数字", "只允许输入英文字母和数字。"));
        definitions.add(regex("builtin_email", "邮箱",
                "^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$",
                "请输入正确的邮箱地址", "校验常用邮箱地址格式。"));
        definitions.add(regex("builtin_domain", "域名",
                "^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.?$",
                "请输入正确的域名", "校验常用域名格式。"));
        definitions.add(regex("builtin_mobile", "手机号", "^1[0-9]{10}$",
                "请输入正确的手机号", "校验中国大陆11位手机号格式。"));
        definitions.add(regex("builtin_id_card", "身份证号",
                "^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9])|(1[0-2]))"
                        + "(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$",
                "请输入正确的身份证号", "校验中国大陆18位身份证号的基础格式。"));
        definitions.add(regex("builtin_ip_address", "IP地址",
                "^((?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}"
                        + "(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d))$",
                "请输入正确的IP地址", "校验 IPv4 地址格式。"));
        return definitions;
    }

    static boolean isReservedCode(String code) {
        return code != null && RESERVED_CODES.contains(code);
    }

    static boolean isBuiltin(RuleFieldValidation rule) {
        return rule != null
                && RuleFieldValidationService.SCOPE_GLOBAL.equals(rule.getScope())
                && Long.valueOf(0L).equals(rule.getProjectId())
                && isReservedCode(rule.getValidationCode());
    }

    private static RuleFieldValidation regex(String code, String name, String pattern,
                                             String errorMessage, String description) {
        return rule(code, name, "REGEX", pattern, errorMessage, description);
    }

    private static RuleFieldValidation rule(String code, String name, String type, String value,
                                            String errorMessage, String description) {
        RuleFieldValidation rule = new RuleFieldValidation();
        rule.setProjectId(0L);
        rule.setScope(RuleFieldValidationService.SCOPE_GLOBAL);
        rule.setValidationCode(code);
        rule.setValidationName(name);
        rule.setValidationType(type);
        rule.setValidationValue(value);
        rule.setErrorMessage(errorMessage);
        rule.setDescription(description);
        rule.setStatus(1);
        rule.setBuiltIn(true);
        return rule;
    }
}
