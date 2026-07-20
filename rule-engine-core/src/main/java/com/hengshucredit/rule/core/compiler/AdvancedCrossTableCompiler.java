package com.hengshucredit.rule.core.compiler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 复杂交叉表编译器：支持多维行/列交叉 + 区间匹配。
 * 将多维分段的笛卡尔积生成 if/else if 链，每条为所有维度条件的 AND 组合。
 * 通过 {@link VarContext} 将 varCode 解析为正确的 scriptName。
 *
 * <p>VarContext 通过参数传递，不使用 ThreadLocal。
 */
public class AdvancedCrossTableCompiler implements RuleCompiler {

    @Override
    public CompileResult compile(String modelJson) {
        return compile(modelJson, null);
    }

    @Override
    public CompileResult compile(String modelJson, VarContext varContext) {
        return doCompile(modelJson, varContext);
    }

    private CompileResult doCompile(String modelJson, VarContext varContext) {
        try {
            JSONObject model = JSON.parseObject(modelJson);
            JSONArray rowDims = model.getJSONArray("rowDimensions");
            JSONArray colDims = model.getJSONArray("colDimensions");
            JSONObject resultVar = model.getJSONObject("resultVar");
            JSONArray cells = model.getJSONArray("cells");

            if (resultVar == null) {
                return CompileResult.fail("复杂交叉表缺少结果变量定义");
            }
            Long resVarId = resultVar.containsKey("_varId") ? resultVar.getLong("_varId") : null;
            String resRefType = resultVar.getString("_refType");
            String resCode = resultVar.getString("varCode");
            String resType = resultVar.getString("varType");
            JSONObject resultOperand = resultVar.getJSONObject("operand");
            String resolvedResCode = resultOperand != null
                    ? OperandCompiler.compile(resultOperand, varContext)
                    : resolveVar(resVarId, resRefType, resCode, varContext);

            if (rowDims == null || rowDims.isEmpty()) {
                return CompileResult.fail("复杂交叉表缺少行维度定义");
            }
            if (colDims == null || colDims.isEmpty()) {
                return CompileResult.fail("复杂交叉表缺少列维度定义");
            }
            if (cells == null) {
                return CompileResult.fail("复杂交叉表缺少单元格数据");
            }

            List<List<SegmentInfo>> rowProduct = cartesianProduct(rowDims);
            List<List<SegmentInfo>> colProduct = cartesianProduct(colDims);

            StringBuilder script = new StringBuilder();
            boolean first = true;

            for (int ri = 0; ri < rowProduct.size(); ri++) {
                List<SegmentInfo> rowSegs = rowProduct.get(ri);
                JSONArray rowCells = cells.getJSONArray(ri);
                if (rowCells == null) continue;

                for (int ci = 0; ci < colProduct.size(); ci++) {
                    List<SegmentInfo> colSegs = colProduct.get(ci);
                    Object cellValue = getCellValue(rowCells, ci);
                    if (cellValue == null || (cellValue instanceof String && ((String) cellValue).trim().isEmpty())) continue;

                    script.append(first ? "if (" : " else if (");
                    first = false;

                    boolean firstCond = true;
                    for (SegmentInfo seg : rowSegs) {
                        if (!firstCond) script.append(" && ");
                        firstCond = false;
                        appendCondition(script, seg, varContext);
                    }
                    for (SegmentInfo seg : colSegs) {
                        if (!firstCond) script.append(" && ");
                        firstCond = false;
                        appendCondition(script, seg, varContext);
                    }

                    script.append(") {\n    ").append(resolvedResCode).append(" = ");
                    if (cellValue instanceof JSONObject) {
                        appendTypedOperand(script, (JSONObject) cellValue, resType, varContext);
                    } else {
                        appendValue(script, String.valueOf(cellValue), resType);
                    }
                    script.append("\n}");
                }
            }
            if (!first) {
                script.append("\n");
            }
            LinkedHashSet<String> outVars = new LinkedHashSet<>();
            outVars.add(resolvedResCode);
            RuleScriptResultCollector.prependOutputNullInits(script, outVars);
            RuleScriptResultCollector.appendResultMapReturn(script, outVars);

            return CompileResult.ok(script.toString(), "QLEXPRESS");
        } catch (Exception e) {
            return CompileResult.fail("复杂交叉表编译失败: " + e.getMessage());
        }
    }

    /** 计算所有维度分段的笛卡尔积 */
    private List<List<SegmentInfo>> cartesianProduct(JSONArray dimensions) {
        List<List<SegmentInfo>> result = new ArrayList<>();
        result.add(new ArrayList<>());

        for (int d = 0; d < dimensions.size(); d++) {
            JSONObject dim = dimensions.getJSONObject(d);
            String varCode = dim.getString("varCode");
            String varType = dim.getString("varType");
            Long varId = dim.containsKey("_varId") ? dim.getLong("_varId") : null;
            String refType = dim.getString("_refType");
            JSONObject dimensionOperand = dim.getJSONObject("operand");
            JSONArray segments = dim.getJSONArray("segments");

            List<List<SegmentInfo>> newResult = new ArrayList<>();
            for (List<SegmentInfo> existing : result) {
                for (int s = 0; s < segments.size(); s++) {
                    JSONObject seg = segments.getJSONObject(s);
                    List<SegmentInfo> newList = new ArrayList<>(existing);
                    newList.add(new SegmentInfo(varCode, varType, varId, refType, dimensionOperand,
                            seg.getString("operator"),
                            seg.getString("value"),
                            seg.getString("min"),
                            seg.getString("max"),
                            normalizeRangeBoundary(seg.getString("rangeBoundary")),
                            seg.getJSONObject("valueOperand"),
                            seg.getJSONObject("minOperand"),
                            seg.getJSONObject("maxOperand")));
                    newResult.add(newList);
                }
            }
            result = newResult;
        }
        return result;
    }

    /** 从多层嵌套的 cells 数组中取值 */
    private Object getCellValue(JSONArray rowCells, int colIndex) {
        try {
            Object val = rowCells.get(colIndex);
            if (val instanceof JSONArray) {
                return ((JSONArray) val).get(0);
            }
            return val;
        } catch (Exception e) {
            return null;
        }
    }

    /** 将单个分段条件追加到脚本 */
    private void appendCondition(StringBuilder sb, SegmentInfo seg, VarContext varContext) {
        String scriptName = seg.dimensionOperand != null
                ? OperandCompiler.compile(seg.dimensionOperand, varContext)
                : resolveVar(seg.varId, seg.refType, seg.varCode, varContext);
        String op = seg.operator;

        if ("range".equals(op)) {
            boolean dateType = isDateType(seg.varType);
            if (dateType) sb.append("dateToMillis(");
            sb.append(scriptName);
            if (dateType) sb.append(")");
            sb.append(seg.rangeBoundary.startsWith("[") ? " >= " : " > ");
            appendRangeValue(sb, seg.minOperand, seg.min, seg.varType, varContext, dateType);
            sb.append(" && ");
            if (dateType) sb.append("dateToMillis(");
            sb.append(scriptName);
            if (dateType) sb.append(")");
            sb.append(seg.rangeBoundary.endsWith("]") ? " <= " : " < ");
            appendRangeValue(sb, seg.maxOperand, seg.max, seg.varType, varContext, dateType);
        } else {
            JSONObject leaf = new JSONObject();
            leaf.put("leftOperand", dimensionOperand(seg));
            leaf.put("operator", op);
            leaf.put("rightOperand", segmentOperand(seg.valueOperand, seg.value, seg.varType));
            sb.append(ConditionOperandCompiler.compile(leaf, varContext));
        }
    }

    private JSONObject dimensionOperand(SegmentInfo seg) {
        if (seg.dimensionOperand != null) return seg.dimensionOperand;
        JSONObject operand = new JSONObject();
        operand.put("kind", seg.varId != null || (seg.refType != null && !seg.refType.trim().isEmpty())
                ? "REFERENCE" : "PATH");
        operand.put("code", seg.varCode);
        operand.put("value", seg.varCode);
        operand.put("valueType", seg.varType);
        if (seg.varId != null) operand.put("refId", seg.varId);
        if (seg.refType != null) operand.put("refType", seg.refType);
        return operand;
    }

    private JSONObject segmentOperand(JSONObject operand, String value, String valueType) {
        if (operand != null) return operand;
        if (value == null) return null;
        JSONObject literal = new JSONObject();
        literal.put("kind", "LITERAL");
        literal.put("value", value);
        literal.put("valueType", valueType);
        return literal;
    }

    private void appendRangeValue(StringBuilder sb, JSONObject operand, String legacyValue,
                                  String legacyType, VarContext varContext, boolean dateType) {
        if (dateType) sb.append("dateToMillis(");
        appendSegmentValue(sb, operand, legacyValue, legacyType, varContext);
        if (dateType) sb.append(")");
    }

    private void appendSegmentValue(StringBuilder sb, JSONObject operand, String legacyValue,
                                    String legacyType, VarContext varContext) {
        if (operand != null) {
            appendTypedOperand(sb, operand, legacyType, varContext);
        } else {
            appendValue(sb, legacyValue, legacyType);
        }
    }

    private void appendTypedOperand(StringBuilder sb, JSONObject operand, String expectedType,
                                    VarContext varContext) {
        if ("LITERAL".equals(operand.getString("kind"))) {
            sb.append(OperandCompiler.compileLiteral(operand.get("value"), expectedType));
        } else {
            sb.append(OperandCompiler.compile(operand, varContext));
        }
    }

    private String resolveVar(Long varId, String varCode, VarContext varContext) {
        return resolveVar(varId, null, varCode, varContext);
    }

    private String resolveVar(Long varId, String refType, String varCode, VarContext varContext) {
        boolean managedReference = varId != null || (refType != null && !refType.trim().isEmpty());
        if (managedReference && varContext == null) {
            throw new IllegalArgumentException("受管字段引用缺少变量上下文");
        }
        if (varContext != null) return varContext.resolveVar(varId, refType, varCode);
        return varCode != null ? varCode : "";
    }

    private static void appendValue(StringBuilder sb, String value, String type) {
        if ("STRING".equals(type) || "ENUM".equals(type)) {
            sb.append("\"").append(value.replace("\"", "\\\"")).append("\"");
        } else {
            sb.append(value);
        }
    }

    private static String normalizeRangeBoundary(String rangeBoundary) {
        if ("[)".equals(rangeBoundary) || "()".equals(rangeBoundary)
                || "[]".equals(rangeBoundary) || "(]".equals(rangeBoundary)) {
            return rangeBoundary;
        }
        return "[)";
    }

    private static boolean isDateType(String varType) {
        if (varType == null) return false;
        String type = varType.trim().toUpperCase();
        return "DATE".equals(type) || "DATETIME".equals(type) || "TIMESTAMP".equals(type)
                || "LOCALDATE".equals(type) || "LOCALDATETIME".equals(type);
    }

    /** 维度分段信息 */
    private static class SegmentInfo {
        final String varCode;
        final String varType;
        final Long varId;
        final String refType;
        final JSONObject dimensionOperand;
        final String operator;
        final String value;
        final String min;
        final String max;
        final String rangeBoundary;
        final JSONObject valueOperand;
        final JSONObject minOperand;
        final JSONObject maxOperand;

        SegmentInfo(String varCode, String varType, Long varId, String refType, JSONObject dimensionOperand,
                    String operator, String value, String min, String max, String rangeBoundary,
                    JSONObject valueOperand, JSONObject minOperand, JSONObject maxOperand) {
            this.varCode = varCode;
            this.varType = varType;
            this.varId = varId;
            this.refType = refType;
            this.dimensionOperand = dimensionOperand;
            this.operator = operator;
            this.value = value;
            this.min = min;
            this.max = max;
            this.rangeBoundary = rangeBoundary;
            this.valueOperand = valueOperand;
            this.minOperand = minOperand;
            this.maxOperand = maxOperand;
        }
    }
}
