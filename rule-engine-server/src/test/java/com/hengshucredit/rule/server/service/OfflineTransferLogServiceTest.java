package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hengshucredit.rule.server.mapper.OfflineTransferLogMapper;
import com.hengshucredit.rule.server.sql.TestSchemaTables;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.hengshucredit.rule.server.transfer.TransferBundle;
import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import com.hengshucredit.rule.server.transfer.OfflineResourceTransferService.ExportedPackage;
import com.hengshucredit.rule.server.transfer.TransferBundleCodec;
import com.hengshucredit.rule.server.transfer.TransferRootRequest;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflineTransferLogServiceTest {
    @Test
    public void literalLogSearchCombinesFieldsBeforeCountingAndPaging() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:transferSearch;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var db = new JdbcTemplate(source);
        try {
            TestSchemaTables.create(db, List.of("offline_transfer_log"));
            var config = new MybatisConfiguration();
            config.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setConfiguration(config);
            factory.setDataSource(source);
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            factory.setPlugins(plugins);
            var sessions = factory.getObject();
            sessions.getConfiguration().addMapper(OfflineTransferLogMapper.class);
            var service = new OfflineTransferLogService();
            ReflectionTestUtils.setField(service, "baseMapper", new SqlSessionTemplate(sessions).getMapper(OfflineTransferLogMapper.class));

            String insert = """
                    INSERT INTO offline_transfer_log(id, operation_type, status, operator, package_digest, roots_json, content_json, error_message, create_time)
                    VALUES (?, ?, ?, 'operatorA', 'abc123', '["RULE:37"]', ?, ?, '2026-11-20 12:00:00')
                    """;
            db.update(insert, 1, "IMPORT", "FAILED", "{\"code\":\"risk_%\"}", "缺少依赖");
            db.update(insert, 2, "IMPORT", "FAILED", "{}", "risk_%");
            db.update(insert, 3, "EXPORT", "FAILED", "{\"code\":\"risk_%\"}", null);
            db.update(insert, 4, "IMPORT", "SUCCESS", "{\"code\":\"risk_%\"}", null);
            db.update(insert, 5, "IMPORT", "FAILED", "{\"code\":\"riskAB\"}", null);

            var page = service.page(2, 1, "IMPORT", "FAILED", " risk_% ", true);
            assertEquals(2, page.getTotal());
            assertEquals(List.of(1L), page.getRecords().stream().map(OfflineTransferLog::getId).toList());
            org.junit.Assert.assertNull(page.getRecords().get(0).getContentJson());
            for (String term : List.of("operatorA", "abc123", "RULE:37")) {
                assertEquals(5, service.page(1, 20, null, null, term, true).getTotal());
            }
            assertEquals(0, service.page(1, 20, null, null, "未出现", true).getTotal());
            assertEquals("{\"code\":\"risk_%\"}", service.getById(1L).getContentJson());
        } finally {
            db.execute("SHUTDOWN");
        }
    }

    @Test
    public void failedImportKeepsVerifiedPackageIdentityAndLineageWithoutSnapshots() {
        var bundle = new TransferBundle(List.of("RULE:1"), List.of(new TransferBundle.Resource("RULE:1",
                Map.of("ruleCode", "check", "description", "private configuration body"), List.of(), List.of())), List.of());
        var codec = new TransferBundleCodec();
        var decoded = codec.decodeDeferred(codec.encode(bundle));
        var logs = new java.util.ArrayList<OfflineTransferLog>();
        var service = new OfflineTransferLogService() {
            @Override public boolean save(OfflineTransferLog log) { return logs.add(log); }
        };
        service.recordDecodedImport(decoded, decoded.packageDigest(), Map.of("taskId", "abc"), "reviewer", "目标项目冲突");
        var log = logs.get(0);
        assertEquals("FAILED", log.getStatus());
        assertEquals(decoded.packageDigest(), log.getPackageDigest());
        var content = com.alibaba.fastjson.JSON.parseObject(log.getContentJson());
        assertEquals("RULE:1", content.getJSONArray("roots").getString(0));
        assertEquals("check", content.getJSONObject("lineage").getJSONArray("nodes").getJSONObject(0).getString("code"));
        assertEquals("abc", content.getJSONObject("result").getString("taskId"));
        org.junit.Assert.assertFalse(log.getContentJson().contains("private configuration body"));
    }

    @Test
    public void exportCountsSharedDependencyOnceAndKeepsRequestedRootsSeparate() {
        var dependency = new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null);
        var roots = List.of("RULE:1", "RULE:2");
        var bundle = new TransferBundle(roots, List.of(
                new TransferBundle.Resource("RULE:1", Map.of("dependencyId", 9), List.of(dependency), List.of()),
                new TransferBundle.Resource("RULE:2", Map.of("dependencyId", 9), List.of(dependency), List.of()),
                new TransferBundle.Resource("VARIABLE:9", Map.of("varCode", "score"), List.of(), List.of())), List.of());
        var logs = new java.util.ArrayList<OfflineTransferLog>();
        var service = new OfflineTransferLogService() {
            @Override public boolean save(OfflineTransferLog log) { return logs.add(log); }
        };
        service.recordExport(List.of(new TransferRootRequest("RULE", 1L), new TransferRootRequest("RULE", 2L)),
                new ExportedPackage(bundle, new TransferBundleCodec().encode(bundle)), "tester");
        var log = logs.get(0);
        assertEquals(Integer.valueOf(3), log.getResourceCount());
        assertEquals(roots, com.alibaba.fastjson.JSON.parseArray(log.getRootsJson(), String.class));
        assertEquals(3, com.alibaba.fastjson.JSON.parseObject(log.getContentJson()).getJSONObject("lineage")
                .getJSONArray("nodes").size());
    }
    @Test
    public void lineageSnapshotKeepsExistingNodesAndSkipsMissingReferences() {
        OfflineTransferLogService service = new OfflineTransferLogService();
        TransferBundle bundle = new TransferBundle(List.of("RULE:1"), List.of(
                new TransferBundle.Resource("RULE:1", Map.of("ruleCode", "risk"), List.of(
                        new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null)), List.of()),
                new TransferBundle.Resource("VARIABLE:9", Map.of("varCode", "score"), List.of(), List.of())), List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(service, "lineageSnapshot", bundle, List.of("RULE:1"));

        assertEquals(1, ((List<?>) graph.get("nodes")).size());
        assertTrue(((List<?>) graph.get("edges")).isEmpty());
    }

    @Test
    public void lineageSnapshotContainsEdgesWhenBothResourcesExist() {
        OfflineTransferLogService service = new OfflineTransferLogService();
        TransferBundle bundle = new TransferBundle(List.of("RULE:1"), List.of(
                new TransferBundle.Resource("RULE:1", Map.of("ruleCode", "risk"), List.of(
                        new TransferBundle.Reference("/dependencyId", "VARIABLE:9", null)), List.of()),
                new TransferBundle.Resource("VARIABLE:9", Map.of("varCode", "score"), List.of(), List.of())), List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> graph = ReflectionTestUtils.invokeMethod(service, "lineageSnapshot", bundle, null);

        assertEquals(2, ((List<?>) graph.get("nodes")).size());
        assertEquals(1, ((List<?>) graph.get("edges")).size());
    }
}
