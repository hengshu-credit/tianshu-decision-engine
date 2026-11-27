package com.hengshucredit.rule.server.search;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.entity.RuleExecutionLog;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Fetch bodies only for a page of already authorized physical records in the caller's read
 * snapshot.
 */
@Service
@ConditionalOnProperty(name = "rule-engine.search.enabled", havingValue = "true")
@Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ)
public class LogSearchPages {
    private final ElasticLogClient es;
    private final LogSearchDocuments documents;
    private final JdbcTemplate jdbc;

    public LogSearchPages(ElasticLogClient es, LogSearchDocuments documents, JdbcTemplate jdbc) {
        this.es = es;
        this.documents = documents;
        this.jdbc = jdbc;
    }

    public IPage<RuleExecutionLog> executions(
            Supplier<IPage<ElasticLogClient.Ref>> refs) {
        return page(
                refs.get(),
                ElasticLogClient.EXECUTION,
                "rule_execution_log",
                RuleExecutionLog.class);
    }

    public IPage<RuleRuntimeCallLog> calls(
            Supplier<IPage<ElasticLogClient.Ref>> refs) {
        return page(
                refs.get(),
                ElasticLogClient.CALL,
                "rule_runtime_call_log",
                RuleRuntimeCallLog.class);
    }

    /** A dashboard may consume dimensions directly when every authorized revision is available. */
    public <T> Optional<List<T>> dimensions(
            String kind,
            String fields,
            String scopedIdentitySql,
            List<Object> args,
            Function<JsonNode, T> convert) {
        var refs =
                jdbc.query(
                        scopedIdentitySql + " LIMIT 50001",
                        (rs, i) ->
                                new ElasticLogClient.Ref(
                                        rs.getLong(1),
                                        rs.getObject(2, LocalDateTime.class),
                                        rs.getLong(3)),
                        args.toArray());
        if (refs.size() > 50000) return Optional.empty();
        List<T> results = new ArrayList<>(refs.size());
        for (int offset = 0; offset < refs.size(); offset += 512) {
            var batch = refs.subList(offset, Math.min(offset + 512, refs.size()));
            var docs = es.fields(kind, batch, fields);
            if (docs.size() != batch.size()) return Optional.empty();
            for (var ref : batch) results.add(convert.apply(docs.get(ref)));
        }
        return Optional.of(results);
    }

    private <T> IPage<T> page(
            IPage<ElasticLogClient.Ref> refs, String kind, String table, Class<T> type) {
        var page = new Page<T>(refs.getCurrent(), refs.getSize(), refs.getTotal());
        var values = new ArrayList<T>();
        var mapper = BeanPropertyRowMapper.newInstance(type);
        for (int offset = 0; offset < refs.getRecords().size(); offset += 32) {
            var batch =
                    refs.getRecords()
                            .subList(offset, Math.min(offset + 32, refs.getRecords().size()));
            var bodies = es.bodies(kind, batch);
            var found = new HashMap<ElasticLogClient.Ref, T>();
            var missing = new ArrayList<ElasticLogClient.Ref>();
            for (var ref : batch) {
                T value = documents.open(kind, ref, bodies.get(ref), type);
                if (value == null) missing.add(ref);
                else found.put(ref, value);
            }
            if (!missing.isEmpty()) {
                String tuples = String.join(",", Collections.nCopies(missing.size(), "(?,?)"));
                List<Object> args = new ArrayList<>();
                for (var ref : missing) {
                    args.add(ref.id());
                    args.add(ref.time());
                }
                String extra =
                        ElasticLogClient.EXECUTION.equals(kind)
                                ? ","
                                        + com.hengshucredit.rule.server.mapper
                                                .RuleExecutionLogMapper.RESTORED_TRACE
                                        + " AS stored_trace"
                                : "";
                jdbc.query(
                        "SELECT *"
                                + extra
                                + " FROM "
                                + table
                                + " WHERE (id,create_time) IN ("
                                + tuples
                                + ")",
                        rs -> {
                            var ref =
                                    new ElasticLogClient.Ref(
                                            rs.getLong("id"),
                                            rs.getObject("create_time", LocalDateTime.class),
                                            rs.getLong("es_revision"));
                            T record = mapper.mapRow(rs, 0);
                            if (record instanceof RuleExecutionLog log)
                                log.setTraceInfo(rs.getString("stored_trace"));
                            found.put(ref, record);
                        },
                        args.toArray());
            }
            for (var ref : batch) {
                T value = found.get(ref);
                if (value == null) throw new IllegalStateException("日志读取快照不完整");
                values.add(value);
            }
        }
        return page.setRecords(values);
    }
}
