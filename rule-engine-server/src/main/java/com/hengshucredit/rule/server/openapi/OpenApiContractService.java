package com.hengshucredit.rule.server.openapi;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleDefinitionInputField;
import com.hengshucredit.rule.model.entity.RuleDefinitionOutputField;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RulePublished;
import com.hengshucredit.rule.server.mapper.RuleProjectAuthMapper;
import com.hengshucredit.rule.server.mapper.RulePublishedMapper;
import com.hengshucredit.rule.server.artifact.PublishedRuleFieldSnapshotResolver;
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleFieldAnalyzer;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenApiContractService {

    @Resource
    private RuleProjectAuthMapper authMapper;

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RulePublishedMapper publishedMapper;

    @Resource
    private RuleDefinitionService definitionService;

    @Resource
    private PublishedRuleFieldSnapshotResolver publishedFieldSnapshotResolver;

    public ResolvedContract resolve(Long authenticatedAuthId, String authCode, String ruleCode) {
        if (authenticatedAuthId == null) {
            throw new OpenApiException(OpenApiStatuses.productUnauthorized());
        }
        if (!hasText(authCode)) {
            throw new OpenApiException(OpenApiStatuses.requiredField("请求头 X-Auth-Code 不能为空"));
        }
        LambdaQueryWrapper<RuleProjectAuth> authQuery = new LambdaQueryWrapper<RuleProjectAuth>()
                .eq(RuleProjectAuth::getAuthCode, authCode.trim())
                .eq(RuleProjectAuth::getStatus, 1);
        authQuery.eq(RuleProjectAuth::getId, authenticatedAuthId);
        RuleProjectAuth auth = authMapper.selectOne(authQuery);
        if (auth == null) throw new OpenApiException(OpenApiStatuses.productUnauthorized());
        RuleProject project = projectService.getById(auth.getProjectId());
        if (project == null || !Integer.valueOf(1).equals(project.getStatus())) {
            throw new OpenApiException(OpenApiStatuses.accountDisabled());
        }
        PublishedOpenApiContract projection = publishedMapper.selectOpenApiContract(
                project.getId(), project.getProjectCode(), ruleCode);
        if (projection == null || !hasText(projection.getOpenApiConfigJson())) {
            throw new OpenApiException(OpenApiStatuses.requestProductUnauthorized());
        }
        OpenApiContract contract = OpenApiContractCodec.parse(projection.getOpenApiConfigJson());
        if (!contract.isEnabled()) {
            throw new OpenApiException(OpenApiStatuses.requestProductUnauthorized());
        }
        new OpenResponseRenderer().validate(contract);
        List<RuleDefinitionInputField> inputs;
        List<RuleDefinitionOutputField> outputs;
        if (projection.getArtifactId() != null || projection.getRevisionId() != null) {
            RuleFieldAnalyzer.ResolvedFields fields = publishedFieldSnapshotResolver.resolve(projection);
            if (fields.getDiagnostics().stream().anyMatch(issue -> "ERROR".equals(issue.getSeverity()))) {
                throw new OpenApiException(OpenApiStatuses.systemError());
            }
            inputs = fields.getInputFields();
            outputs = fields.getOutputFields();
        } else {
            // 仅无制品的历史发布沿用原字段解析；坏制品不得退回当前工作草稿。
            inputs = definitionService.listInputFields(projection.getDefinitionId());
            outputs = definitionService.listOutputFields(projection.getDefinitionId());
        }
        return new ResolvedContract(auth, project, projection, contract,
                inputs, inputReferences(inputs), outputReferences(outputs));
    }

    public OpenApiContract defaultContract() {
        OpenApiContract contract = new OpenApiContract();
        contract.setEnvelopeTemplate(JSON.parse("{\"success\":\"${status.success}\",\"code\":\"${status.code}\","
                + "\"message\":\"${status.message}\",\"traceId\":\"${traceId}\",\"data\":\"${data}\"}"));
        contract.setDataPath("$.data");
        contract.setSuccessDataTemplate("${response}");
        contract.setErrorDataTemplate(JSON.parse("{\"errorCode\":\"${status.code}\",\"errorMessage\":\"${status.message}\"}"));
        return contract;
    }

    private Map<String, String> inputReferences(List<RuleDefinitionInputField> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        if (fields == null) return result;
        for (RuleDefinitionInputField field : fields) {
            if (field.getStatus() != null && !Integer.valueOf(1).equals(field.getStatus())) continue;
            String key = OpenRequestMapper.referenceKey(field.getRefType(), field.getVarId());
            if (key != null && hasText(field.getScriptName())) result.put(key, field.getScriptName().trim());
        }
        return result;
    }

    private Map<String, String> outputReferences(List<RuleDefinitionOutputField> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        if (fields == null) return result;
        for (RuleDefinitionOutputField field : fields) {
            if (field.getStatus() != null && !Integer.valueOf(1).equals(field.getStatus())) continue;
            String key = OpenRequestMapper.referenceKey(field.getRefType(), field.getVarId());
            if (key != null && hasText(field.getScriptName())) result.put(key, field.getScriptName().trim());
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class ResolvedContract {
        private final RuleProjectAuth auth;
        private final RuleProject project;
        private final RulePublished published;
        private final OpenApiContract contract;
        private final List<RuleDefinitionInputField> inputFields;
        private final Map<String, String> inputReferences;
        private final Map<String, String> outputReferences;

        public ResolvedContract(RuleProjectAuth auth, RuleProject project, RulePublished published,
                                OpenApiContract contract, List<RuleDefinitionInputField> inputFields,
                                Map<String, String> inputReferences,
                                Map<String, String> outputReferences) {
            this.auth = auth;
            this.project = project;
            this.published = published;
            this.contract = contract;
            this.inputFields = inputFields == null ? List.of() : List.copyOf(inputFields);
            this.inputReferences = inputReferences;
            this.outputReferences = outputReferences;
        }

        public RuleProjectAuth getAuth() { return auth; }
        public RuleProject getProject() { return project; }
        public RulePublished getPublished() { return published; }
        public OpenApiContract getContract() { return contract; }
        public List<RuleDefinitionInputField> getInputFields() { return inputFields; }
        public Map<String, String> getInputReferences() { return inputReferences; }
        public Map<String, String> getOutputReferences() { return outputReferences; }
    }
}
