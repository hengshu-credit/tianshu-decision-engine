package com.hengshucredit.rule.server.sql;

import com.baomidou.mybatisplus.annotation.TableName;
import org.apache.ibatis.annotations.Mapper;
import org.junit.Assert;
import org.junit.Test;

public class ConsoleRbacEntityMappingTest {

    @Test
    public void entitiesAndMappersCoverEveryAuthorizationTable() throws Exception {
        assertMapping("ConsoleUser", "console_user");
        assertMapping("ConsoleRole", "console_role");
        assertMapping("ConsolePermission", "console_permission");
        assertMapping("ConsoleUserRole", "console_user_role");
        assertMapping("ConsoleRolePermission", "console_role_permission");
        assertMapping("ConsoleUserPermissionOverride", "console_user_permission_override");
        assertMapping("ConsoleSecurityAuditLog", "console_security_audit_log");
    }

    private static void assertMapping(String simpleName, String tableName) throws Exception {
        Class<?> entityClass = Class.forName(
                "com.hengshucredit.rule.model.entity." + simpleName);
        TableName annotation = entityClass.getAnnotation(TableName.class);
        Assert.assertNotNull(simpleName + " missing @TableName", annotation);
        Assert.assertEquals(tableName, annotation.value());

        Class<?> mapperClass = Class.forName(
                "com.hengshucredit.rule.server.mapper." + simpleName + "Mapper");
        Assert.assertNotNull(simpleName + "Mapper missing @Mapper",
                mapperClass.getAnnotation(Mapper.class));
    }
}
