package com.hengshucredit.rule.core.engine;

import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.api.parsecache.LoadedParseCache;
import com.alibaba.qlexpress4.runtime.context.ExpressContext;
import com.alibaba.qlexpress4.runtime.context.MapExpressContext;
import com.alibaba.qlexpress4.runtime.context.ObjectFieldExpressContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QLExpressEngine {

    private static final Logger log = LoggerFactory.getLogger(QLExpressEngine.class);

    private static final int MAX_PREPARED_SCRIPTS = 1024;
    private static final QLOptions NORMAL_OPTIONS = QLOptions.builder().traceExpression(false).build();
    private static final QLOptions TRACE_OPTIONS = QLOptions.builder().traceExpression(true).build();
    private final Express4Runner runner;
    private final Map<String, PreparedScript> preparedScripts = new ConcurrentHashMap<>();

    public QLExpressEngine() {
        this.runner = new Express4Runner(InitOptions.builder()
                .traceExpression(true)
                .securityStrategy(QLExpressScriptSecurity.standardFunctionWhitelist())
                .build());
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    public QLExpressEngine(InitOptions initOptions) {
        this.runner = new Express4Runner(initOptions);
        AggregateBuiltinFunctionRegistry.register(this.runner);
    }

    /** Parse, bind and collect static checks once. The cache is local to this runner and bounded. */
    public PreparedScript prepare(String script) {
        if (script == null || script.isBlank()) throw new IllegalArgumentException("QL 脚本不能为空");
        PreparedScript cached = preparedScripts.get(script);
        if (cached != null) return cached.requireValid();
        synchronized (preparedScripts) {
            cached = preparedScripts.get(script);
            if (cached != null) return cached.requireValid();
            PreparedScript prepared;
            try {
                LoadedParseCache loaded = runner.loadSerializableCache(runner.parseToSerializableCache(script));
                prepared = new PreparedScript(this, loaded, ScriptStaticChecks.assignmentRoots(runner, script), null);
            } catch (RuntimeException invalid) {
                prepared = new PreparedScript(this, null, Collections.emptySet(), invalid);
            }
            if (preparedScripts.size() >= MAX_PREPARED_SCRIPTS) {
                preparedScripts.remove(preparedScripts.keySet().iterator().next());
            }
            preparedScripts.put(script, prepared);
            return prepared.requireValid();
        }
    }

    /** Publication/preview validation uses frozen constant metadata, not a running request. */
    public PreparedScript prepare(String script, Set<String> constantNames) {
        PreparedScript prepared = prepare(script);
        ScriptStaticChecks.assertDoesNotAssignConstants(prepared.assignmentRoots, constantNames);
        return prepared;
    }

    public void clearPreparedScripts() {
        synchronized (preparedScripts) {
            preparedScripts.clear();
            runner.clearCompileCache();
        }
    }

    public RuleResult execute(String script, Map<String, Object> context) {
        return execute(script, context, false);
    }

    public RuleResult execute(String script, Map<String, Object> context, boolean trace) {
        return execute(script, (Object) context, trace);
    }

    /** Compatibility entry point for ad-hoc callers; published rules are prepared before activation. */
    public RuleResult execute(String script, Object context, boolean trace) {
        return executeInternal(null, script, context, trace, RuntimeContextBridge.executionContext());
    }

    public RuleResult execute(PreparedScript script, Object context, boolean trace) {
        return execute(script, context, trace, RuntimeContextBridge.executionContext());
    }

    public RuleResult execute(PreparedScript script, Object context, boolean trace, RequestContext request) {
        return executeInternal(script, null, context, trace, request);
    }

    @SuppressWarnings("unchecked")
    private RuleResult executeInternal(PreparedScript prepared, String source, Object context,
                                       boolean trace, RequestContext request) {
        RuleResult ruleResult = new RuleResult();
        long start = System.currentTimeMillis();
        boolean protectConstants = request.containsRegisteredConstants(context);
        int runtimeWriteMarker = request.beginRuntimeWriteScope();
        try (RuntimeContextBridge.ContextScope ignored = RuntimeContextBridge.install(request)) {
            if (prepared == null) prepared = prepare(source);
            if (prepared.owner != this) throw new IllegalArgumentException("预编译脚本不能跨执行器复用");
            // Runtime policy check against precomputed targets; no source scanning on execution.
            ScriptStaticChecks.assertDoesNotAssignConstants(prepared.assignmentRoots, request.constantNames());
            ExpressContext expressContext = context == null || context instanceof Map
                    ? new MapExpressContext(context == null ? Collections.emptyMap() : (Map<String, Object>) context)
                    : new ObjectFieldExpressContext(context, runner);
            QLResult result = runner.execute(prepared.loaded, expressContext, trace ? TRACE_OPTIONS : NORMAL_OPTIONS);
            request.syncTraceAssignments(result.getExpressionTraces(), context);
            request.replayRuntimeWrites(runtimeWriteMarker, context);
            if (protectConstants) request.assertConstantsUnchanged(context);
            ruleResult.setResult(result.getResult());
            ruleResult.setSuccess(true);
            if (trace && result.getExpressionTraces() != null) {
                ruleResult.setTraces(Collections.singletonList(result.getExpressionTraces()));
            }
        } catch (StackOverflowError e) {
            if (protectConstants) request.restoreConstants(context);
            log.error("QLExpress execution stack overflow", e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage("QLExpress execution failed: StackOverflowError");
        } catch (Exception e) {
            rethrowControlledTermination(e);
            if (protectConstants) request.restoreConstants(context);
            log.error("QLExpress execution error: {}", e.getMessage(), e);
            ruleResult.setSuccess(false);
            ruleResult.setErrorMessage(e.getMessage());
        } finally {
            request.endRuntimeWriteScope();
            ruleResult.setExecuteTimeMs(System.currentTimeMillis() - start);
        }
        return ruleResult;
    }

    public static final class PreparedScript {
        private final QLExpressEngine owner;
        private final LoadedParseCache loaded;
        private final Set<String> assignmentRoots;
        private final RuntimeException preparationFailure;

        private PreparedScript(QLExpressEngine owner, LoadedParseCache loaded, Set<String> assignmentRoots,
                               RuntimeException preparationFailure) {
            this.owner = owner;
            this.loaded = loaded;
            this.assignmentRoots = assignmentRoots;
            this.preparationFailure = preparationFailure;
        }

        private PreparedScript requireValid() {
            if (preparationFailure != null) throw preparationFailure;
            return this;
        }
    }

    public Express4Runner getRunner() {
        return runner;
    }

    private static void rethrowControlledTermination(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RuleTerminationSignal) {
                throw (RuleTerminationSignal) current;
            }
            current = current.getCause();
        }
    }
}
