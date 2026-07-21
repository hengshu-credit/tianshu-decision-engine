package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleAuthAccessLog;
import com.hengshucredit.rule.server.mapper.RuleAuthAccessLogMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

@Component
public class AuthAccessLogAsyncWriter {
    private final SqlSessionFactory sqlSessionFactory;
    private final BoundedAsyncLogWriter<RuleAuthAccessLog> writer;

    public AuthAccessLogAsyncWriter(ExternalCallProperties properties, SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.writer = new BoundedAsyncLogWriter<>(properties.getAsyncLogQueueCapacity(),
                properties.getAsyncLogBatchSize(), properties.getAsyncLogFlushMillis(),
                properties.getAsyncLogShutdownWaitMillis(), "auth-access-log-writer",
                this::copy, this::writeBatch);
    }

    @PostConstruct
    public void start() { writer.start(); }

    public boolean offer(RuleAuthAccessLog log) { return writer.offer(copy(log)); }

    private void writeBatch(List<RuleAuthAccessLog> batch) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            RuleAuthAccessLogMapper mapper = session.getMapper(RuleAuthAccessLogMapper.class);
            for (RuleAuthAccessLog log : batch) mapper.insert(log);
            session.flushStatements();
            session.commit();
        }
    }

    private RuleAuthAccessLog copy(RuleAuthAccessLog source) {
        if (source == null) return null;
        RuleAuthAccessLog target = new RuleAuthAccessLog();
        target.setProjectId(source.getProjectId());
        target.setProjectCode(source.getProjectCode());
        target.setAuthId(source.getAuthId());
        target.setAuthCode(source.getAuthCode());
        target.setAuthType(source.getAuthType());
        target.setTokenId(source.getTokenId());
        target.setTokenCode(source.getTokenCode());
        target.setAuthPhase(source.getAuthPhase());
        target.setRequestMethod(source.getRequestMethod());
        target.setRequestUri(source.getRequestUri());
        target.setRequestId(source.getRequestId());
        target.setClientIp(source.getClientIp());
        target.setSuccess(source.getSuccess());
        target.setFailureReason(source.getFailureReason());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    @PreDestroy
    public void close() { writer.close(); }
}
