package com.hengshucredit.rule.server.security;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.delete.Delete;

/** 更新/删除同时使用操作权限和项目范围，避免只校验“有任一项目编辑权”。 */
public class ConsoleDataPermissionInterceptor extends DataPermissionInterceptor {
    private final ConsoleDataPermissionHandler policy;
    public ConsoleDataPermissionInterceptor(ConsoleDataPermissionHandler policy) { super(policy); this.policy = policy; }
    @Override protected void processUpdate(Update statement, int index, String sql, Object context) {
        policy.forMutation(() -> { super.processUpdate(statement, index, sql, context); return null; });
    }
    @Override protected void processDelete(Delete statement, int index, String sql, Object context) {
        policy.forMutation(() -> { super.processDelete(statement, index, sql, context); return null; });
    }
}
