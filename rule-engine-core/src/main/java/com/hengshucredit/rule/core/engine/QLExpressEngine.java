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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QLExpressEngine {

    private static final Logger log = LoggerFactory.getLogger(QLExpressEngine.class);

    private static final int MAX_PREPARED_SCRIPTS = 1024;
    /*
     * Keep QLExpress' null-pointer protection disabled deliberately.  With
     * avoidNullPointer=true, an unknown function or a null field access can be
     * converted to a silent null result, which hides a bad rule/function
     * configuration.  Missing values are handled only by the registered
     * comparison operators and by the function implementation itself.
     */
    private static final QLOptions NORMAL_OPTIONS = QLOptions.builder()
            .traceExpression(false)
            .avoidNullPointer(false)
            .build();
    private static final QLOptions TRACE_OPTIONS = QLOptions.builder()
            .traceExpression(true)
            .avoidNullPointer(false)
            .build();
    private final Express4Runner runner;
    private final Map<String, PreparedEntry> preparedScripts = new ConcurrentHashMap<>();
    // Guarded by preparedScripts; cache hits only set the entry's second-chance bit.
    private final Deque<PreparedEntry> preparationOrder = new ArrayDeque<>();

    public QLExpressEngine() {
        this.runner = new Express4Runner(InitOptions.builder()
                .traceExpression(true)
                .securityStrategy(QLExpressScriptSecurity.standardFunctionWhitelist())
                .build());
        AggregateBuiltinFunctionRegistry.register(this.runner);
        MissingValueOperators.register(this.runner);
        registerDecisionTableFunctions();
    }

    public QLExpressEngine(InitOptions initOptions) {
        this.runner = new Express4Runner(initOptions);
        AggregateBuiltinFunctionRegistry.register(this.runner);
        MissingValueOperators.register(this.runner);
        registerDecisionTableFunctions();
    }

    private void registerDecisionTableFunctions() {
        runner.addFunction("UNIQUE_HIT_POLICY_MATCHED_MULTIPLE_RULES", (Runnable) () -> {
            throw new IllegalStateException("UNIQUE hit policy matched multiple rules");
        });
    }

    /** Parse, bind and collect static checks once. The cache is local to this runner and bounded. */
    public PreparedScript prepare(String script) {
        if (script == null || script.isBlank()) throw new IllegalArgumentException("QL 脚本不能为空");
        PreparedEntry cached = preparedScripts.get(script);
        if (cached != null) return cached.access();
        synchronized (preparedScripts) {
            cached = preparedScripts.get(script);
            if (cached != null) return cached.access();
            PreparedScript prepared;
            try {
                LoadedParseCache loaded = runner.loadSerializableCache(runner.parseToSerializableCache(script));
                prepared = new PreparedScript(this, loaded, ScriptStaticChecks.assignmentRoots(runner, script),
                        Collections.unmodifiableSet(new LinkedHashSet<>(runner.getOutFunctions(script))), null);
            } catch (RuntimeException invalid) {
                prepared = new PreparedScript(this, null, Collections.emptySet(), Collections.emptySet(), invalid);
            }
            if (preparedScripts.size() >= MAX_PREPARED_SCRIPTS) {
                // Scan at most one rotation, even when every entry is concurrently accessed.
                int remaining = preparationOrder.size();
                while (remaining-- > 0 && preparationOrder.peekFirst().recentlyUsed) {
                    PreparedEntry candidate = preparationOrder.removeFirst();
                    candidate.recentlyUsed = false;
                    preparationOrder.addLast(candidate);
                }
                preparedScripts.remove(preparationOrder.removeFirst().source);
            }
            PreparedEntry entry = new PreparedEntry(script, prepared);
            preparedScripts.put(script, entry);
            preparationOrder.addLast(entry);
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
            preparationOrder.clear();
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
        try (RuntimeContextBridge.ContextScope ignored = RuntimeContextBridge.install(request);
             RequestContext.ExecutionContextScope executionScope = request.bindExecutionContext(context)) {
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
        private final Set<String> functionNames;
        private final RuntimeException preparationFailure;

        private PreparedScript(QLExpressEngine owner, LoadedParseCache loaded, Set<String> assignmentRoots,
                               Set<String> functionNames, RuntimeException preparationFailure) {
            this.owner = owner;
            this.loaded = loaded;
            this.assignmentRoots = assignmentRoots;
            this.functionNames = functionNames;
            this.preparationFailure = preparationFailure;
        }

        /** 准备阶段提取的外部/自定义函数名，执行阶段不再重新解析脚本。 */
        public Set<String> functionNames() {
            return functionNames;
        }

        private PreparedScript requireValid() {
            if (preparationFailure != null) throw preparationFailure;
            return this;
        }
    }

    private static final class PreparedEntry {
        private final String source;
        private final PreparedScript script;
        private volatile boolean recentlyUsed;

        private PreparedEntry(String source, PreparedScript script) {
            this.source = source;
            this.script = script;
        }

        private PreparedScript access() {
            recentlyUsed = true;
            return script.requireValid();
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
