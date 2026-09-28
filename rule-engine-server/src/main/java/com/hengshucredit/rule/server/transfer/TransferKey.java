package com.hengshucredit.rule.server.transfer;

/** 仅标识源环境资源；不能直接作为目标环境数据库主键。 */
public record TransferKey(TransferResourceType type, long id) {
    public TransferKey {
        if (type == null || id <= 0) throw new IllegalArgumentException("迁移资源必须包含类型和正整数 ID");
    }

    public String value() { return type.name() + ":" + id; }

    public static TransferKey parse(String value) {
        if (value == null || !value.matches("[A-Z_]+:[1-9][0-9]*"))
            throw new IllegalArgumentException("迁移资源键无效: " + value);
        int split = value.indexOf(':');
        try {
            return new TransferKey(TransferResourceType.valueOf(value.substring(0, split)),
                    Long.parseLong(value.substring(split + 1)));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("不支持的迁移资源键: " + value, invalid);
        }
    }
}
