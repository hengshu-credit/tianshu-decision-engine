import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.hengshucredit.rule.core.compiler.*;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.model.dto.RuleResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compiles documentation examples with the local engine; requires no server or business database. */
public class CompileExamples {
    public static void main(String[] args) throws Exception {
        JSONArray examples = JSON.parseArray(Files.readString(Path.of(args[0])));
        Map<String, String> refs = Map.of("VARIABLE:3", "livenessScore", "VARIABLE:13", "faceSimilarity",
                "VARIABLE:4", "verified", "VARIABLE:5", "riskLevel", "VARIABLE:14", "faceQualityScore", "VARIABLE:15", "totalScore");
        VarContext context = new VarContext(Map.of(), Map.of(), refs);
        Map<String, RuleCompiler> compilers = Map.of(
                "TABLE", new DecisionTableCompiler(), "TREE", new DecisionTreeCompiler(),
                "FLOW", new DecisionFlowCompiler(), "RULE_SET", new RuleSetCompiler(),
                "CROSS", new CrossTableCompiler(), "SCORE", new ScorecardCompiler(),
                "CROSS_ADV", new AdvancedCrossTableCompiler(), "SCORE_ADV", new AdvancedScorecardCompiler(),
                "SCRIPT", new ScriptPassthroughCompiler());
        QLExpressEngine engine = new QLExpressEngine();
        for (Object item : examples) {
            JSONObject example = (JSONObject) item;
            CompileResult compiled = compilers.get(example.getString("modelType")).compile(example.getString("modelJson"), context);
            if (!compiled.isSuccess()) throw new IllegalStateException(example.getString("ruleCode") + ": " + compiled.getErrorMessage());
            RuleResult result = engine.execute(compiled.getCompiledScript(), new LinkedHashMap<>(example.getJSONObject("params")), true);
            if (!result.isSuccess()) throw new IllegalStateException(example.getString("ruleCode") + ": " + result.getErrorMessage());
            example.put("compiledScript", compiled.getCompiledScript());
            example.put("execution", JSON.parseObject(JSON.toJSONString(result, SerializerFeature.DisableCircularReferenceDetect)));
            System.out.println(example.getString("ruleCode") + " => " + JSON.toJSONString(result.getResult()));
        }
        Files.writeString(Path.of(args[1]), JSON.toJSONString(examples, SerializerFeature.PrettyFormat, SerializerFeature.DisableCircularReferenceDetect));
    }
}
