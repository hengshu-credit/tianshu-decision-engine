package com.hengshucredit.rule.server.service;

import java.util.Map;

/** 两条名单写入链路共用的业务别名；缺省值、ANY 和大小写策略由调用方处理。 */
final class ListRecordAliases {
    private static final Map<String, String> ITEM_TYPES = Map.ofEntries(
            Map.entry("手机号", "MOBILE"), Map.entry("手机", "MOBILE"),
            Map.entry("身份证", "ID_CARD"), Map.entry("地址", "ADDRESS"),
            Map.entry("设备号", "DEVICE"), Map.entry("设备", "DEVICE"),
            Map.entry("姓名", "NAME"), Map.entry("银行卡", "BANK_CARD"),
            Map.entry("邮箱", "EMAIL"), Map.entry("邮件", "EMAIL"),
            Map.entry("其他", "OTHER"));
    private static final Map<String, String> OPERATIONS = Map.of(
            "新增", "ADD", "添加", "ADD", "修改", "UPDATE",
            "更新", "UPDATE", "删除", "DELETE", "停用", "DELETE");

    private ListRecordAliases() { }

    static String itemType(String value) {
        return ITEM_TYPES.get(value);
    }

    static String operation(String value) {
        return OPERATIONS.get(value);
    }
}
