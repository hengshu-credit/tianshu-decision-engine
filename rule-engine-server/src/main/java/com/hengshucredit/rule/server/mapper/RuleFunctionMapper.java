package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.RuleFunction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuleFunctionMapper extends BaseMapper<RuleFunction> {
    /** 只清除未使用的实现列；其他未提交的字段沿用局部更新语义。 */
    default int updateImplementation(RuleFunction function) {
        if (function.getImplType() == null) return updateById(function);
        var update = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RuleFunction>()
                .eq(RuleFunction::getId, function.getId());
        switch (function.getImplType()) {
            case "SCRIPT" -> {
                function.setImplClass(null); function.setImplMethod(null); function.setImplBeanName(null);
                update.set(RuleFunction::getImplClass, null).set(RuleFunction::getImplMethod, null).set(RuleFunction::getImplBeanName, null);
            }
            case "JAVA" -> {
                function.setImplScript(null); function.setImplBeanName(null);
                update.set(RuleFunction::getImplScript, null).set(RuleFunction::getImplBeanName, null);
            }
            case "BEAN" -> {
                function.setImplScript(null); function.setImplClass(null);
                update.set(RuleFunction::getImplScript, null).set(RuleFunction::getImplClass, null);
            }
            default -> { return updateById(function); }
        }
        return update(function, update);
    }
}
