package com.hengshucredit.rule.core.compiler;

import lombok.Data;
import com.hengshucredit.rule.model.PlatformCode;
import com.hengshucredit.rule.model.PlatformOutcome;

@Data
public class CompileResult implements PlatformOutcome {
    private boolean success;
    private String compiledScript;
    private String compiledType;
    private String errorMessage;

    public String getCode() { return platformStatus().code(); }
    public String getMessage() { return platformStatus().message(); }
    public String getDetail() { return platformDetail(); }
    @Override public PlatformCode platformStatus() {
        return success ? PlatformCode.SUCCESS : PlatformCode.RULE_COMPILE_ERROR;
    }
    @Override public String platformDetail() { return success ? null : errorMessage; }

    public static CompileResult ok(String script, String type) {
        CompileResult r = new CompileResult();
        r.setSuccess(true);
        r.setCompiledScript(script);
        r.setCompiledType(type);
        return r;
    }

    public static CompileResult fail(String error) {
        CompileResult r = new CompileResult();
        r.setSuccess(false);
        r.setErrorMessage(error);
        return r;
    }
}
