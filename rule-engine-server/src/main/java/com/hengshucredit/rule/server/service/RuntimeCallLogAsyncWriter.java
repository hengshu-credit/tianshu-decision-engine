package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.mapper.RuleRuntimeCallLogMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

@Component
public class RuntimeCallLogAsyncWriter {

    private final SqlSessionFactory sqlSessionFactory;
    private final BoundedAsyncLogWriter<RuleRuntimeCallLog> writer;

    public RuntimeCallLogAsyncWriter(ExternalCallProperties properties, SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.writer = new BoundedAsyncLogWriter<>(properties.getAsyncLogQueueCapacity(),
                properties.getAsyncLogBatchSize(), properties.getAsyncLogFlushMillis(),
                properties.getAsyncLogShutdownWaitMillis(), "runtime-call-log-writer",
                this::metadataOnly, this::writeBatch);
    }

    @PostConstruct
    public void start() {
        writer.start();
    }

    public boolean offer(RuleRuntimeCallLog log) {
        return writer.offer(copy(log));
    }

    public long getDroppedBodies() { return writer.getDroppedBodies(); }
    public long getDroppedLogs() { return writer.getDroppedLogs(); }

    private void writeBatch(List<RuleRuntimeCallLog> batch) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            RuleRuntimeCallLogMapper mapper = session.getMapper(RuleRuntimeCallLogMapper.class);
            for (RuleRuntimeCallLog log : batch) mapper.insert(log);
            session.flushStatements();
            session.commit();
        }
    }

    private RuleRuntimeCallLog metadataOnly(RuleRuntimeCallLog source) {
        RuleRuntimeCallLog copy = copy(source);
        copy.setRequestHeaders(null);
        copy.setRequestParams(null);
        copy.setRequestBody(null);
        copy.setResponseBody(null);
        return copy;
    }

    private RuleRuntimeCallLog copy(RuleRuntimeCallLog source) {
        if (source == null) return null;
        RuleRuntimeCallLog target = new RuleRuntimeCallLog();
        target.setTraceId(source.getTraceId());
        target.setRuleTraceId(source.getRuleTraceId());
        target.setModuleType(source.getModuleType());
        target.setActionType(source.getActionType());
        target.setProjectId(source.getProjectId());
        target.setProjectCode(source.getProjectCode());
        target.setDatasourceId(source.getDatasourceId());
        target.setRequestId(source.getRequestId());
        target.setTargetRefId(source.getTargetRefId());
        target.setTargetCode(source.getTargetCode());
        target.setTargetName(source.getTargetName());
        target.setSuccess(source.getSuccess());
        target.setRequestSuccess(source.getRequestSuccess());
        target.setFound(source.getFound());
        target.setProviderRequest(source.getProviderRequest());
        target.setCacheStatus(source.getCacheStatus());
        target.setCacheKey(source.getCacheKey());
        target.setAttemptNo(source.getAttemptNo());
        target.setCircuitState(source.getCircuitState());
        target.setTokenCacheStatus(source.getTokenCacheStatus());
        target.setRequestMethod(source.getRequestMethod());
        target.setRequestUrl(source.getRequestUrl());
        target.setRequestHeaders(source.getRequestHeaders());
        target.setRequestParams(source.getRequestParams());
        target.setRequestBody(source.getRequestBody());
        target.setResponseStatus(source.getResponseStatus());
        target.setResponseBody(source.getResponseBody());
        target.setErrorType(source.getErrorType());
        target.setErrorMessage(source.getErrorMessage());
        target.setCostTimeMs(source.getCostTimeMs());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    @PreDestroy
    public void close() {
        writer.close();
    }
}
