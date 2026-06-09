import org.dmg.pmml.*;
import org.dmg.pmml.mining.*;
import org.jpmml.evaluator.*;
import org.jpmml.model.PMMLUtil;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class pmml_test {
    public static void main(String[] args) throws Exception {
        String path = "/Users/xiaoxi/Desktop/itlubber/杰丰越/部署上线评分卡/score_f1.pmml";
        String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));

        ByteArrayInputStream bis = new ByteArrayInputStream(content.getBytes("UTF-8"));
        PMML pmml = PMMLUtil.unmarshal(bis);
        Model firstModel = pmml.getModels().get(0);

        System.out.println("=== 模型信息 ===");
        System.out.println("模型类: " + firstModel.getClass().getName());
        System.out.println("MiningFunction: " + firstModel.getMiningFunction());

        System.out.println("\n=== 入参 (MiningSchema) ===");
        MiningSchema ms = firstModel.getMiningSchema();
        if (ms != null) {
            for (MiningField mf : ms.getMiningFields()) {
                System.out.println("  " + mf.getName().getValue() + " | usage=" + mf.getUsageType());
            }
        }

        System.out.println("\n=== 出参 (Output) ===");
        Output output = firstModel.getOutput();
        if (output != null) {
            for (OutputField of : output.getOutputFields()) {
                System.out.println("  " + of.getName().getValue() + " | resultFeature=" + of.getResultFeature() + " | isFinal=" + of.isFinalResult());
            }
        } else {
            System.out.println("  (顶层无 Output)");
            if (firstModel instanceof MiningModel) {
                MiningModel mm = (MiningModel) firstModel;
                Segmentation seg = mm.getSegmentation();
                if (seg != null && seg.hasSegments()) {
                    List<Segment> segments = seg.getSegments();
                    System.out.println("  Segments 总数: " + segments.size());
                    for (int i = segments.size() - 1; i >= 0; i--) {
                        Segment s = segments.get(i);
                        Model sm = s.getModel();
                        if (sm != null && sm.getOutput() != null) {
                            System.out.println("  --> 找到 Segment " + i + " 的 Output:");
                            for (OutputField of : sm.getOutput().getOutputFields()) {
                                System.out.println("     " + of.getName().getValue() + " | resultFeature=" + of.getResultFeature() + " | isFinal=" + of.isFinalResult());
                            }
                            break;
                        }
                    }
                }
            }
        }

        System.out.println("\n=== 加载 Evaluator ===");
        ModelEvaluatorFactory evalFactory = ModelEvaluatorFactory.newInstance();
        ModelEvaluator<?> evaluator = evalFactory.newModelEvaluator(pmml, firstModel);
        evaluator.verify();
        System.out.println("Summary: " + evaluator.getSummary());

        System.out.println("\n=== getInputFields() ===");
        try {
            List<? extends InputField> inputFields = evaluator.getInputFields();
            for (InputField f : inputFields) {
                System.out.println("  " + f.getName().getValue());
            }
        } catch (Exception e) {
            System.out.println("  FAILED: " + e.getClass().getName() + " - " + e.getMessage());
        }

        System.out.println("\n=== getOutputFields() ===");
        try {
            List<? extends OutputField> outputFields = evaluator.getOutputFields();
            for (OutputField f : outputFields) {
                System.out.println("  " + f.getName().getValue());
            }
        } catch (Exception e) {
            System.out.println("  FAILED: " + e.getClass().getName() + " - " + e.getMessage());
        }

        System.out.println("\n=== 执行预测 ===");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("f_time_since_last_registration", 100);
        params.put("f_time_since_last_login", 200);
        params.put("f_time_since_last_app_open", 300);
        params.put("f_time_since_last_face_verify", 400);
        params.put("f_time_since_last_basic_info_submit", 500);
        params.put("f_time_since_last_coupon_click", 600);

        Map<FieldName, Object> arguments = new LinkedHashMap<>();
        try {
            List<? extends InputField> inputFields = evaluator.getInputFields();
            for (InputField f : inputFields) {
                FieldName name = f.getName();
                String nameStr = name.getValue();
                Object val = params.get(nameStr);
                if (val == null) val = params.get(toCamel(nameStr));
                arguments.put(name, val);
            }
        } catch (Exception e) {
            System.out.println("getInputFields 失败，改用 MiningSchema: " + e.getMessage());
            for (MiningField mf : firstModel.getMiningSchema().getMiningFields()) {
                if (mf.getUsageType() == MiningField.UsageType.TARGET) continue;
                FieldName name = FieldName.create(mf.getName().getValue());
                String nameStr = mf.getName().getValue();
                Object val = params.get(nameStr);
                if (val == null) val = params.get(toCamel(nameStr));
                arguments.put(name, val);
            }
        }

        System.out.println("Arguments:");
        for (Map.Entry<FieldName, Object> e : arguments.entrySet()) {
            System.out.println("  " + e.getKey().getValue() + " = " + e.getValue());
        }

        try {
            Map<FieldName, ?> result = evaluator.evaluate(arguments);
            System.out.println("\nResult keys:");
            for (FieldName key : result.keySet()) {
                Object val = result.get(key);
                System.out.println("  " + key.getValue() + " = " + val + " (" + (val != null ? val.getClass().getSimpleName() : "null") + ")");
            }

            // 提取出参
            List<String> outputFieldNames = new ArrayList<>();
            try {
                for (OutputField of : evaluator.getOutputFields()) {
                    outputFieldNames.add(of.getName().getValue());
                }
            } catch (Exception e) {
                System.out.println("getOutputFields 失败，改用 PMML: " + e.getMessage());
                // fallback to PMML output
                for (MiningField mf : firstModel.getMiningSchema().getMiningFields()) {
                    if (mf.getUsageType() == MiningField.UsageType.TARGET) continue;
                }
                Output pmmlOut = firstModel.getOutput();
                if (pmmlOut == null && firstModel instanceof MiningModel) {
                    MiningModel mm = (MiningModel) firstModel;
                    Segmentation seg = mm.getSegmentation();
                    if (seg != null && seg.hasSegments()) {
                        for (int i = seg.getSegments().size() - 1; i >= 0; i--) {
                            Segment s = seg.getSegments().get(i);
                            if (s.getModel() != null && s.getModel().getOutput() != null) {
                                pmmlOut = s.getModel().getOutput();
                                break;
                            }
                        }
                    }
                }
                if (pmmlOut != null) {
                    for (OutputField of : pmmlOut.getOutputFields()) {
                        if (!of.isFinalResult()) continue;
                        outputFieldNames.add(of.getName().getValue());
                    }
                }
            }

            System.out.println("\n提取的出参字段:");
            for (String fn : outputFieldNames) {
                FieldName fName = FieldName.create(fn);
                Object val = result.get(fName);
                System.out.println("  " + fn + " = " + val);
            }
        } catch (Exception e) {
            System.out.println("evaluate FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    static String toCamel(String name) {
        if (name == null || !name.contains("_")) return name;
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : name.toCharArray()) {
            if (c == '_') { upperNext = true; }
            else if (upperNext) { sb.append(Character.toUpperCase(c)); upperNext = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }
}