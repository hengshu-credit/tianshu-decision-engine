package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.ResolvableType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuleLineageNeighborhoodTest {
    @Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class})
    })
    private static class RowCounter implements Interceptor {
        final AtomicInteger rows = new AtomicInteger();
        @Override public Object intercept(Invocation invocation) throws Throwable { Object result = invocation.proceed(); if (result instanceof List<?> list) rows.addAndGet(list.size()); return result; }
    }

    @Test public void oneHopTraversalDoesNotMaterializeTenThousandUnrelatedBranches() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:lineage_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        var counter = new RowCounter(); configuration.addInterceptor(counter);
        var identity = new java.util.concurrent.atomic.AtomicReference<>(new com.hengshucredit.rule.server.security.ConsoleProjectAccess.Scope(10L, Set.of(1L), Set.of("field:view", "rule:view", "project:view")));
        var access = new com.hengshucredit.rule.server.security.ConsoleProjectAccess() { @Override public Scope current() { return identity.get(); } };
        var permissions = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        permissions.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor(new com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler(access)));
        configuration.addInterceptor(permissions);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration); factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        var sessions = new SqlSessionTemplate(factory.getObject());
        var service = new RuleLineageService();
        try {
            for (var field : RuleLineageService.class.getDeclaredFields()) {
                if (!BaseMapper.class.isAssignableFrom(field.getType())) continue;
                configuration.addMapper(field.getType());
                Class<?> entity = ResolvableType.forClass(field.getType()).as(BaseMapper.class).getGeneric(0).resolve();
                var info = TableInfoHelper.getTableInfo(entity);
                // 读路径测试使用实体元数据生成 H2 表；MySQL 分区/生成列由已有数据库门禁验收。
                var columns = new java.util.ArrayList<String>();
                columns.add("`" + info.getKeyColumn() + "` BIGINT AUTO_INCREMENT PRIMARY KEY");
                for (var column : info.getFieldList()) columns.add("`" + column.getColumn().replace("`", "") + "` " + sqlType(column.getPropertyType()));
                jdbc.execute("CREATE TABLE IF NOT EXISTS `" + info.getTableName() + "` (" + String.join(",", columns) + ")");
                ReflectionTestUtils.setField(service, field.getName(), sessions.getMapper(field.getType()));
            }
            jdbc.update("INSERT INTO rule_project(id,project_code,project_name) VALUES (1,'scale','scale')");
            var ids = java.util.stream.IntStream.rangeClosed(1, 10000).boxed().toList();
            jdbc.batchUpdate("INSERT INTO rule_variable(id,project_id,scope,var_code,var_label,var_type) VALUES (?,1,'PROJECT',?,?,'NUMBER')", ids, 1000, (ps,id) -> { ps.setInt(1,id); ps.setString(2,"V"+id); ps.setString(3,"V"+id); });
            jdbc.batchUpdate("INSERT INTO rule_definition(id,project_id,scope,rule_code,rule_name,model_type) VALUES (?,1,'PROJECT',?,?,'SCRIPT')", ids, 1000, (ps,id) -> { ps.setInt(1,id); ps.setString(2,"R"+id); ps.setString(3,"R"+id); });
            jdbc.batchUpdate("INSERT INTO rule_definition_input_field(definition_id,var_id,ref_type,field_name) VALUES (?,?,'VARIABLE',?)", ids, 1000, (ps,id) -> { ps.setInt(1,id); ps.setInt(2,id); ps.setString(3,"V"+id); });
            long started = System.nanoTime();
            Map<String,Object> result = service.graph("VARIABLE", 1L, "DOWNSTREAM", 1);
            var nodes = (List<?>) result.get("nodes");
            assertEquals(Set.of("VARIABLE:1", "RULE:1"), nodes.stream().map(row -> ((Map<?,?>) row).get("id")).collect(java.util.stream.Collectors.toSet()));
            assertEquals(1, ((List<?>) result.get("edges")).size());
            assertTrue("必须观测到真实数据库读取", counter.rows.get() > 0);
            assertTrue("一跳查询不应读取整个目录，实际行数=" + counter.rows.get(), counter.rows.get() < 100);
            System.out.println("LINEAGE_NEIGHBORHOOD rows=" + counter.rows.get() + " elapsedMs=" + (System.nanoTime()-started)/1_000_000 + " branches=10000");
            counter.rows.set(0);
            var both = service.graph("VARIABLE", 1L, "ALL", 2);
            assertEquals(3, ((List<?>) both.get("nodes")).size());
            assertTrue("上游经过项目时不能读取该项目全部兄弟分支", counter.rows.get() > 0 && counter.rows.get() < 100);
            identity.set(new com.hengshucredit.rule.server.security.ConsoleProjectAccess.Scope(11L, Set.of(2L), Set.of("field:view", "rule:view", "project:view")));
            assertThrows("后续请求必须重新按项目权限过滤起点", IllegalArgumentException.class, () -> service.graph("VARIABLE", 1L, "DOWNSTREAM", 1));
            identity.set(new com.hengshucredit.rule.server.security.ConsoleProjectAccess.Scope(10L, Set.of(1L), Set.of("field:view", "rule:view", "project:view")));
            var limit = assertThrows(IllegalArgumentException.class, () -> service.graph("PROJECT", 1L, "DOWNSTREAM", 1));
            assertTrue(limit.getMessage().contains("未返回不完整图"));
        } finally { jdbc.execute("DROP ALL OBJECTS"); }
    }

    private static String sqlType(Class<?> type) {
        if (type == Integer.class || type == int.class) return "INT";
        if (Number.class.isAssignableFrom(type) || type == long.class) return "BIGINT";
        if (type == Boolean.class || type == boolean.class) return "BOOLEAN";
        if (type == java.time.LocalDateTime.class || java.util.Date.class.isAssignableFrom(type)) return "TIMESTAMP";
        if (type == byte[].class) return "BLOB";
        return "VARCHAR(4096)";
    }
}
