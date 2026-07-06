package com.hengshucredit.rule.server.function;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * 初始化平台级 SCRIPT 函数。
 *
 * <p>仅插入当前库中不存在的同名 GLOBAL 函数，绝不覆盖用户已经维护的脚本。</p>
 */
@Component
@ConditionalOnProperty(prefix = "rule-engine.builtin-functions", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BuiltinScriptFunctionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BuiltinScriptFunctionInitializer.class);
    private static final long GLOBAL_PROJECT_ID = 0L;
    private static final String GLOBAL_SCOPE = "GLOBAL";
    private static final String SCRIPT_TYPE = "SCRIPT";

    @Resource
    private RuleFunctionMapper ruleFunctionMapper;

    @Override
    public void run(ApplicationArguments args) {
        for (BuiltinFunctionTemplate template : templates()) {
            seedIfAbsent(template);
        }
    }

    private void seedIfAbsent(BuiltinFunctionTemplate template) {
        RuleFunction existing = ruleFunctionMapper.selectOne(new LambdaQueryWrapper<RuleFunction>()
                .eq(RuleFunction::getProjectId, GLOBAL_PROJECT_ID)
                .eq(RuleFunction::getScope, GLOBAL_SCOPE)
                .eq(RuleFunction::getFuncCode, template.funcCode));
        if (existing != null) {
            return;
        }

        RuleFunction function = new RuleFunction();
        function.setProjectId(GLOBAL_PROJECT_ID);
        function.setScope(GLOBAL_SCOPE);
        function.setFuncCode(template.funcCode);
        function.setFuncName(template.funcName);
        function.setDescription(template.description);
        function.setParamsJson(template.paramsJson);
        function.setReturnType(template.returnType);
        function.setImplType(SCRIPT_TYPE);
        function.setImplScript(template.script);
        function.setStatus(1);

        try {
            ruleFunctionMapper.insert(function);
            log.info("[BuiltinFunction] initialized GLOBAL SCRIPT function: {}", template.funcCode);
        } catch (DuplicateKeyException e) {
            // 多实例同时启动时，唯一索引负责最终一致性；已有实例写入后无需再处理。
            log.debug("[BuiltinFunction] GLOBAL SCRIPT function already exists: {}", template.funcCode);
        }
    }

    private List<BuiltinFunctionTemplate> templates() {
        return Arrays.asList(
                new BuiltinFunctionTemplate(
                        "idCardGender",
                        "身份证提取性别",
                        "支持 15/18 位中国居民身份证；女返回 0，男返回 1，格式或出生日期无效返回 -1。",
                        "[{\"name\":\"idCard\",\"type\":\"STRING\",\"label\":\"身份证号\"}]",
                        "NUMBER",
                        "return idCardGenderValue(idCard);"),
                new BuiltinFunctionTemplate(
                        "idCardBirthDate",
                        "身份证提取出生年月日",
                        "支持 15/18 位中国居民身份证；返回 DATE 对象，格式或出生日期无效返回 null。",
                        "[{\"name\":\"idCard\",\"type\":\"STRING\",\"label\":\"身份证号\"}]",
                        "OBJECT",
                        "return idCardBirthDateValue(idCard);"),
                new BuiltinFunctionTemplate(
                        "strLeft",
                        "字符串提取前N位",
                        "提取 text 前 length 个字符；length 大于等于字符串长度时返回全部字符。",
                        "[{\"name\":\"text\",\"type\":\"STRING\",\"label\":\"字符串\"},"
                                + "{\"name\":\"length\",\"type\":\"NUMBER\",\"label\":\"提取长度N\"}]",
                        "STRING",
                        "return leftStringValue(text, length);"),
                new BuiltinFunctionTemplate(
                        "strRight",
                        "字符串提取后N位",
                        "提取 text 后 length 个字符；length 大于等于字符串长度时返回全部字符。",
                        "[{\"name\":\"text\",\"type\":\"STRING\",\"label\":\"字符串\"},"
                                + "{\"name\":\"length\",\"type\":\"NUMBER\",\"label\":\"提取长度N\"}]",
                        "STRING",
                        "return rightStringValue(text, length);"),
                new BuiltinFunctionTemplate(
                        "idCardAge",
                        "身份证计算年龄",
                        "currentTime 不传时使用系统当前日期；mode=YEAR/0/按年相减按年份相减，"
                                + "mode=YMD/EXACT/1/按年月日相减或不传按完整年月日计算周岁；无效时返回 -1。",
                        "[{\"name\":\"idCard\",\"type\":\"STRING\",\"label\":\"身份证号\"},"
                                + "{\"name\":\"currentTime\",\"type\":\"DATE\",\"label\":\"当前时间（可选）\"},"
                                + "{\"name\":\"mode\",\"type\":\"STRING\",\"label\":\"年龄计算方式（可选）\"}]",
                        "NUMBER",
                        "return idCardAgeValue(idCard, currentTime, mode);"),
                new BuiltinFunctionTemplate(
                        "regexMatch",
                        "字符串匹配正则表达式",
                        "完整匹配返回 1，不匹配、空值或非法正则返回 0；包含匹配请在正则前后添加 .*。",
                        "[{\"name\":\"text\",\"type\":\"STRING\",\"label\":\"待匹配字符串\"},"
                                + "{\"name\":\"regex\",\"type\":\"STRING\",\"label\":\"正则表达式\"}]",
                        "NUMBER",
                        "return regexMatchValue(text, regex);")
        );
    }

    private static final class BuiltinFunctionTemplate {
        private final String funcCode;
        private final String funcName;
        private final String description;
        private final String paramsJson;
        private final String returnType;
        private final String script;

        private BuiltinFunctionTemplate(String funcCode, String funcName, String description,
                                        String paramsJson, String returnType, String script) {
            this.funcCode = funcCode;
            this.funcName = funcName;
            this.description = description;
            this.paramsJson = paramsJson;
            this.returnType = returnType;
            this.script = script;
        }
    }
}
