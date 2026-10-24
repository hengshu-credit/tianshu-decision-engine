package com.hengshucredit.rule.server.system;

/** 仅传递已脱敏的通知摘要，秘密和验证令牌不进入系统事件。 */
public record SystemNotification(String title, String text) { }
