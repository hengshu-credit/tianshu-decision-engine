package com.hengshucredit.rule.server.security;

public class ConsoleAccessDeniedException extends RuntimeException {
    public ConsoleAccessDeniedException(String permission) { super("没有操作权限: " + permission); }
}
