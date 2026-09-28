package com.hengshucredit.rule.server.publish;

import com.hengshucredit.rule.model.dto.RulePushMessage;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.service.RuleFunctionService;
import com.hengshucredit.rule.server.service.RuleProjectService;
import org.springframework.stereotype.Service;
import java.util.UUID;

import jakarta.annotation.Resource;

@Service
public class RuleFunctionPushService {

    @Resource
    private RuleProjectService projectService;

    @Resource
    private RulePushService pushService;

    public RulePushMessage prepare(RuleFunction function, String action) {
        if (function == null) {
            throw new IllegalArgumentException("函数不能为空");
        }
        RulePushMessage message = new RulePushMessage();
        message.setOperationId(UUID.randomUUID().toString());
        message.setAction(action);
        message.setFuncCode(function.getFuncCode());
        message.setFuncName(function.getFuncName());
        message.setFuncImplType(function.getImplType());
        message.setFuncImplScript(function.getImplScript());
        message.setFuncImplClass(function.getImplClass());
        message.setFuncImplMethod(function.getImplMethod());
        message.setFuncImplBeanName(function.getImplBeanName());
        message.setFuncParamsJson(function.getParamsJson());
        message.setPublishTime(System.currentTimeMillis());
        populateScope(function, message);
        return message;
    }

    public void push(RulePushMessage message) {
        pushService.push(message);
    }

    private void populateScope(RuleFunction function,
                               RulePushMessage message) {
        if (RuleFunctionService.SCOPE_GLOBAL.equals(function.getScope())) {
            message.setScope(RuleFunctionService.SCOPE_GLOBAL);
            message.setProjectCode(null);
            return;
        }
        if (function.getScope() != null && !function.getScope().isEmpty()
                && !RuleFunctionService.SCOPE_PROJECT.equals(
                function.getScope())) {
            throw new IllegalArgumentException(
                    "函数作用域无效: " + function.getScope());
        }
        if (function.getProjectId() == null || function.getProjectId() <= 0) {
            throw new IllegalArgumentException("项目函数必须关联有效项目");
        }
        RuleProject project = projectService.getById(function.getProjectId());
        if (project == null || project.getProjectCode() == null
                || project.getProjectCode().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "项目函数所属项目不存在: " + function.getProjectId());
        }
        message.setScope(RuleFunctionService.SCOPE_PROJECT);
        message.setProjectCode(project.getProjectCode());
    }
}
