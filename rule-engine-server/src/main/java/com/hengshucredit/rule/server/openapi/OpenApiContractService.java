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
import com.hengshucredit.rule.server.service.RuleDefinitionService;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
        RulePublished published = publishedMapper.selectOne(new LambdaQueryWrapper<RulePublished>()
                .eq(RulePublished::getDefinitionId, projection.getDefinitionId())
                .eq(RulePublished::getStatus, 1));
        if (published == null) throw new OpenApiException(OpenApiStatuses.requestProductUnauthorized());
        return new ResolvedContract(auth, project, published, contract,
                inputReferences(projection.getDefinitionId()), outputReferences(projection.getDefinitionId()));
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

    private Map<String, String> inputReferences(Long definitionId) {
        Map<String, String> result = new LinkedHashMap<>();
        List<RuleDefinitionInputField> fields = definitionService.listInputFields(definitionId);
        if (fields == null) return result;
        for (RuleDefinitionInputField field : fields) {
            if (Integer.valueOf(0).equals(field.getStatus())) continue;
            String key = OpenRequestMapper.referenceKey(field.getRefType(), field.getVarId());
            if (key != null && hasText(field.getScriptName())) result.put(key, field.getScriptName().trim());
        }
        return result;
    }

    private Map<String, String> outputReferences(Long definitionId) {
        Map<String, String> result = new LinkedHashMap<>();
        List<RuleDefinitionOutputField> fields = definitionService.listOutputFields(definitionId);
        if (fields == null) return result;
        for (RuleDefinitionOutputField field : fields) {
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
        private final Map<String, String> inputReferences;
        private final Map<String, String> outputReferences;

        public ResolvedContract(RuleProjectAuth auth, RuleProject project, RulePublished published,
                                OpenApiContract contract, Map<String, String> inputReferences,
                                Map<String, String> outputReferences) {
            this.auth = auth;
            this.project = project;
            this.published = published;
            this.contract = contract;
            this.inputReferences = inputReferences;
            this.outputReferences = outputReferences;
        }

        public RuleProjectAuth getAuth() { return auth; }
        public RuleProject getProject() { return project; }
        public RulePublished getPublished() { return published; }
        public OpenApiContract getContract() { return contract; }
        public Map<String, String> getInputReferences() { return inputReferences; }
        public Map<String, String> getOutputReferences() { return outputReferences; }
    }
}
