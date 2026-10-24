package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hengshucredit.rule.model.entity.RuleSftpDatasource;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import com.hengshucredit.rule.server.mapper.RuleSftpDatasourceMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class RuleSftpDatasourceService extends ServiceImpl<RuleSftpDatasourceMapper, RuleSftpDatasource> {
    @Resource private RuleProjectMapper projectMapper;
    @Resource private SftpConnectPools sftpConnectPools;
    @Resource private SftpFileCache sftpFileCache;
    @Resource private ProjectFilterService projectFilterService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.hengshucredit.rule.server.governance.DatasourceDraftCredentials draftCredentials;

    public IPage<RuleSftpDatasource> pageList(int pageNum, int pageSize, String scope, Long projectId,
                                              String projectCode, String projectName,
                                              String datasourceCode, String datasourceName, Integer status) {
        ProjectFilterService.ProjectMatches matches = projectFilterService.resolve(projectCode, projectName);
        if (matches.isActive() && matches.isEmpty()) return new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RuleSftpDatasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(RuleSftpDatasource::getStatus, -1);
        if (hasText(scope)) wrapper.eq(RuleSftpDatasource::getScope, scope);
        if (projectId != null && projectId > 0) wrapper.eq(RuleSftpDatasource::getProjectId, projectId);
        if (matches.isActive()) wrapper.in(RuleSftpDatasource::getProjectId, matches.getProjectIds());
        if (hasText(datasourceCode)) wrapper.like(RuleSftpDatasource::getDatasourceCode, datasourceCode);
        if (hasText(datasourceName)) wrapper.like(RuleSftpDatasource::getDatasourceName, datasourceName);
        if (status != null) wrapper.eq(RuleSftpDatasource::getStatus, status);
        wrapper.orderByDesc(RuleSftpDatasource::getCreateTime);
        IPage<RuleSftpDatasource> page = page(new Page<>(pageNum, pageSize), wrapper);
        fillProjectName(page.getRecords());
        hideSecrets(page.getRecords());
        return page;
    }

    public void saveWithDefaults(RuleSftpDatasource datasource) { fillDefaults(datasource); save(datasource); }

    public RuleSftpDatasource getDetail(Long id) {
        RuleSftpDatasource datasource = getById(id);
        if (datasource != null) {
            fillProjectName(List.of(datasource));
            hideSecrets(List.of(datasource));
        }
        return datasource;
    }

    public void updateWithDefaults(RuleSftpDatasource datasource) {
        fillDefaults(datasource);
        updateById(datasource);
        sftpConnectPools.refresh(datasource.getId());
        sftpFileCache.invalidateDatasource(datasource.getId());
    }

    public void deleteDatasource(Long id) { removeById(id); sftpConnectPools.refresh(id); sftpFileCache.invalidateDatasource(id); }

    public void testConnection(Long id) throws Exception {
        RuleSftpDatasource datasource = getById(id);
        if (datasource == null) throw new IllegalArgumentException("SFTP数据源不存在");
        sftpConnectPools.testConnection(withDefaults(datasource));
    }

    public void testConnection(RuleSftpDatasource datasource) throws Exception {
        var cleared = com.hengshucredit.rule.server.governance.CredentialChanges.clearFields(
                datasource.getClearCredentials(), com.hengshucredit.rule.server.governance.CredentialChanges.SFTP_FIELDS,
                new org.springframework.beans.BeanWrapperImpl(datasource)::getPropertyValue);
        RuleSftpDatasource trial = new RuleSftpDatasource();
        org.springframework.beans.BeanUtils.copyProperties(datasource, trial);
        if (trial.getId() != null) {
            RuleSftpDatasource stored = getById(trial.getId());
            if (stored == null) throw new IllegalArgumentException("SFTP数据源不存在");
            Map<String, Object> draft = draftCredentials == null ? Map.of() : draftCredentials.load("SFTP", trial.getId());
            if (!hasText(trial.getPassword())) trial.setPassword((String) draft.getOrDefault("password", stored.getPassword()));
            if (!hasText(trial.getPrivateKey())) trial.setPrivateKey((String) draft.getOrDefault("privateKey", stored.getPrivateKey()));
            if (!hasText(trial.getPassphrase())) trial.setPassphrase((String) draft.getOrDefault("passphrase", stored.getPassphrase()));
            if (!hasText(trial.getKnownHosts())) trial.setKnownHosts((String) draft.getOrDefault("knownHosts", stored.getKnownHosts()));
        }
        var trialProperties = new org.springframework.beans.BeanWrapperImpl(trial);
        cleared.forEach(field -> trialProperties.setPropertyValue(field, ""));
        sftpConnectPools.testConnection(withDefaults(trial));
    }

    private RuleSftpDatasource withDefaults(RuleSftpDatasource datasource) { fillDefaults(datasource); return datasource; }

    private void fillDefaults(RuleSftpDatasource datasource) {
        if (datasource == null) throw new IllegalArgumentException("SFTP数据源不能为空");
        Map<String, String> issues = SftpConfigurationValidator.issues(datasource);
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.values().iterator().next());
        if (!hasText(datasource.getScope())) datasource.setScope(RuleVariableService.SCOPE_PROJECT);
        if (RuleVariableService.SCOPE_GLOBAL.equals(datasource.getScope())) datasource.setProjectId(0L);
        if (datasource.getProjectId() == null) datasource.setProjectId(0L);
        if (!hasText(datasource.getConnectionMode())) datasource.setConnectionMode("DIRECT");
        if (datasource.getPort() == null || datasource.getPort() <= 0) datasource.setPort(22);
        if (datasource.getConnectTimeoutMs() == null || datasource.getConnectTimeoutMs() <= 0) datasource.setConnectTimeoutMs(10000);
        if (datasource.getSessionTimeoutMs() == null || datasource.getSessionTimeoutMs() <= 0) datasource.setSessionTimeoutMs(30000);
        if (datasource.getSocketTimeoutMs() == null || datasource.getSocketTimeoutMs() <= 0) datasource.setSocketTimeoutMs(30000);
        if (datasource.getMaxPoolSize() == null || datasource.getMaxPoolSize() <= 0) datasource.setMaxPoolSize(8);
        if (datasource.getMinIdle() == null || datasource.getMinIdle() < 0) datasource.setMinIdle(1);
        if (datasource.getMinIdle() > datasource.getMaxPoolSize()) datasource.setMinIdle(datasource.getMaxPoolSize());
        if (datasource.getBorrowTimeoutMs() == null || datasource.getBorrowTimeoutMs() <= 0) datasource.setBorrowTimeoutMs(5000);
        if (datasource.getIdleTimeoutMs() == null || datasource.getIdleTimeoutMs() <= 0) datasource.setIdleTimeoutMs(600000);
        if (datasource.getMaxFileSizeBytes() == null || datasource.getMaxFileSizeBytes() <= 0) datasource.setMaxFileSizeBytes(10 * 1024 * 1024);
        if (datasource.getMaxFileSizeBytes() > SftpConnectPools.MAX_SAFE_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("SFTP单文件读取上限不能超过 " + SftpConnectPools.MAX_SAFE_FILE_SIZE_BYTES + " 字节");
        }
        if (!hasText(datasource.getFileNameEncoding())) datasource.setFileNameEncoding("UTF-8");
        if (datasource.getStrictHostKeyChecking() == null) datasource.setStrictHostKeyChecking(false);
        if (datasource.getStatus() == null) datasource.setStatus(1);
    }

    private void fillProjectName(List<RuleSftpDatasource> list) {
        ProjectDisplaySupport.fill(list, RuleSftpDatasource::getProjectId, projectMapper, (row, project) -> {
            row.setProjectName(project == null ? null : project.getProjectName());
        });
    }

    private void hideSecrets(List<RuleSftpDatasource> list) {
        if (list == null) return;
        list.forEach(item -> {
            item.setConfiguredSecrets(Map.of("password", hasText(item.getPassword()),
                    "privateKey", hasText(item.getPrivateKey()), "passphrase", hasText(item.getPassphrase()),
                    "knownHosts", hasText(item.getKnownHosts())));
            item.setPassword(null);
            item.setPrivateKey(null);
            item.setPassphrase(null);
            item.setKnownHosts(null);
        });
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
