package com.hengshucredit.rule.server.consolelogin;

import cn.hutool.crypto.digest.BCrypt;
import com.hengshucredit.rule.model.entity.ConsolePermission;
import com.hengshucredit.rule.model.entity.ConsoleRole;
import com.hengshucredit.rule.model.entity.ConsoleUser;
import com.hengshucredit.rule.server.mapper.ConsolePermissionMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRoleMapper;
import com.hengshucredit.rule.server.mapper.ConsoleRolePermissionMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseConsoleAccountServiceTest {

    @Test
    public void loginCannotInitializeAnEmptyDatabase() {
        InMemoryService service = new InMemoryService();
        service.userCount = 0;

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("admin", "secret");

        Assert.assertFalse(result.authenticated());
        Assert.assertNull(service.savedUser);
    }

    @Test
    public void databaseAccountsDisableBuiltinFallback() {
        InMemoryService service = new InMemoryService();
        service.userCount = 1;

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("legacy-admin", "secret");

        Assert.assertFalse(result.authenticated());
        Assert.assertNull(service.savedUser);
    }

    @Test
    public void disabledDatabaseAccountCannotLogin() {
        InMemoryService service = new InMemoryService();
        service.userCount = 1;
        service.databaseUser = user(9L, "reviewer", "secret", 0);

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("reviewer", "secret");

        Assert.assertFalse(result.authenticated());
    }

    @Test
    public void enabledDatabaseAccountUsesBcryptPassword() {
        InMemoryService service = new InMemoryService();
        service.userCount = 1;
        service.databaseUser = user(9L, "reviewer", "secret", 1);

        DatabaseConsoleAccountService.ConsoleLoginResult result =
                service.authenticate("reviewer", "secret");

        Assert.assertTrue(result.authenticated());
        Assert.assertFalse(result.bootstrapped());
        Assert.assertEquals(9L, result.userId().longValue());
        Assert.assertEquals(Set.of("approval:view"), result.permissions());
        Assert.assertEquals(1, service.catalogSyncCalls);
    }

    @Test
    public void catalogSyncDoesNotRestoreRevokedSuperAdminPermission()
            throws Exception {
        CatalogSyncService service = new CatalogSyncService();
        AtomicInteger insertedGrants = new AtomicInteger();
        ConsolePermission existingPermission = new ConsolePermission();
        existingPermission.setId(18L);
        existingPermission.setPermissionCode("rule:edit");
        existingPermission.setStatus(1);
        ConsoleRole superAdmin = new ConsoleRole();
        superAdmin.setId(2L);
        superAdmin.setRoleCode("SUPER_ADMIN");

        setField(service, "permissionMapper", mapper(
                ConsolePermissionMapper.class, (method, returnType, args) -> {
                    if ("selectOne".equals(method)) return existingPermission;
                    if ("selectList".equals(method)) {
                        return List.of(existingPermission);
                    }
                    return defaultValue(returnType);
                }));
        setField(service, "roleMapper", mapper(
                ConsoleRoleMapper.class, (method, returnType, args) ->
                        "selectOne".equals(method)
                                ? superAdmin
                                : defaultValue(returnType)));
        setField(service, "rolePermissionMapper", mapper(
                ConsoleRolePermissionMapper.class,
                (method, returnType, args) -> {
                    if ("selectList".equals(method)) return List.of();
                    if ("insert".equals(method)) {
                        insertedGrants.incrementAndGet();
                        return 1;
                    }
                    return defaultValue(returnType);
                }));

        service.synchronize("admin");

        Assert.assertEquals(0, insertedGrants.get());
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Field field = DatabaseConsoleAccountService.class
                .getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(Class<T> type, MapperInvocation invocation) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(
                        method.getName(), method.getReturnType(), args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }

    private interface MapperInvocation {
        Object invoke(String method, Class<?> returnType, Object[] args);
    }

    private static ConsoleUser user(Long id, String username,
                                    String password, int status) {
        ConsoleUser user = new ConsoleUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setStatus(status);
        user.setPermissionVersion(1L);
        return user;
    }

    private static class InMemoryService extends DatabaseConsoleAccountService {
        private long userCount;
        private ConsoleUser databaseUser;
        private ConsoleUser savedUser;
        private int catalogSyncCalls;

        @Override
        protected long countUsers() {
            return userCount;
        }

        @Override
        protected ConsoleUser findUser(String username) {
            return databaseUser != null
                    && databaseUser.getUsername().equals(username)
                    ? databaseUser : null;
        }

        @Override
        protected void persistBootstrapUser(ConsoleUser user) {
            user.setId(1L);
            savedUser = user;
            databaseUser = user;
            userCount = 1;
        }

        @Override
        protected List<String> resolveRoleCodes(Long userId) {
            return userId != null && userId == 9L
                    ? List.of("REVIEWER")
                    : List.of("SUPER_ADMIN");
        }

        @Override
        protected Set<String> resolvePermissions(Long userId) {
            return userId != null && userId == 9L
                    ? Set.of("approval:view")
                    : Set.of("account:manage", "role:manage");
        }

        @Override
        protected void updateLastLogin(ConsoleUser user) {
        }

        @Override
        protected void synchronizePermissionCatalogForExistingUsers(
                String operator) {
            catalogSyncCalls++;
        }
    }

    private static class CatalogSyncService
            extends DatabaseConsoleAccountService {
        private void synchronize(String operator) {
            synchronizePermissionCatalogForExistingUsers(operator);
        }
    }
}
