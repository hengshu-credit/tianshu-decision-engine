package com.hengshucredit.rule.server.security;

import com.hengshucredit.rule.model.entity.AccountOwnedRecord;
import org.springframework.stereotype.Component;

@Component
public class ConsoleRecordOwnership {
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private ConsoleProjectAccess access;

    /** 入队之前调用；后台落库保留已经捕获的身份，业务 Token 不冒充控制台账户。 */
    public void capture(AccountOwnedRecord record) {
        if (record == null) return;
        if (record instanceof com.hengshucredit.rule.model.entity.GovernedResourceVersion version
                && "SYSTEM_MIGRATION".equals(version.getCreateBy())) return;
        var scope = access.current();
        if (scope != null) record.setActorUserId(scope.userId());
    }

    public Long currentUserId() {
        var scope = access.current();
        return scope == null ? null : scope.userId();
    }
}
