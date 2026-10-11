package com.hengshucredit.rule.server.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.mapper.RuleFunctionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class BuiltinFunctionMetadataInitializerTest {
    @Test public void startupOnlyInsertsMissingDefinitionsAndNeverOverwritesExistingConfiguration() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "initializer-test"), RuleFunction.class);
        RuleFunction existing = new RuleFunction(); existing.setId(501L); existing.setFuncCode("numAdd");
        existing.setFuncName("客户金额计算"); existing.setDescription("已审批的业务说明");
        existing.setImplType("JAVA"); existing.setImplClass("com.hengshucredit.rule.core.function.DecisionBuiltinFunctions");
        existing.setImplMethod("numAdd"); existing.setReturnType("NUMBER"); existing.setStatus(1);
        existing.setParamsJson("[{\"name\":\"Loan_Amount\",\"type\":\"NUMBER\",\"javaType\":\"double\"},{\"name\":\"Extra_Amount\",\"type\":\"NUMBER\",\"javaType\":\"double\"}]");
        AtomicInteger updates = new AtomicInteger();
        List<String> inserted = new ArrayList<>();
        RuleFunctionMapper mapper = (RuleFunctionMapper) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RuleFunctionMapper.class}, (proxy, method, args) -> {
            if ("selectOne".equals(method.getName())) {
                LambdaQueryWrapper<?> query = (LambdaQueryWrapper<?>) args[0]; query.getSqlSegment();
                return query.getParamNameValuePairs().containsValue("numAdd") ? existing : null;
            }
            if ("update".equals(method.getName())) { updates.incrementAndGet(); return 1; }
            if ("insert".equals(method.getName())) { inserted.add(((RuleFunction) args[0]).getFuncCode()); return 1; }
            throw new UnsupportedOperationException(method.getName());
        });
        BuiltinFunctionMetadataInitializer initializer = new BuiltinFunctionMetadataInitializer();
        ReflectionTestUtils.setField(initializer, "functionMapper", mapper);
        initializer.initialize();
        assertEquals("启动不能越过审批覆盖现有配置", 0, updates.get());
        assertFalse(inserted.contains("numAdd"));
        assertTrue(inserted.contains("sum"));
    }
}
