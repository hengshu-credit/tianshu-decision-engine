package com.hengshucredit.rule.core.script;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.aparser.RuleContext;
import com.alibaba.qlexpress4.aparser.ParseTree;
import com.alibaba.qlexpress4.aparser.QLParser;
import com.alibaba.qlexpress4.aparser.QLParserBaseVisitor;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.hengshucredit.rule.core.engine.QLExpressEngineFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QLScriptAnalyzer {

    private final Express4Runner runner = QLExpressEngineFactory.getInstance();

    public QLScriptAnalysis analyze(String script) {
        if (script == null || script.isBlank()) {
            return QLScriptAnalysis.parseError("QL_PARSE_ERROR", "$", "QL 脚本不能为空");
        }
        try {
            runner.check(script);
            QLParser.ProgramContext tree = runner.parseToSyntaxTree(script);
            AnalysisVisitor visitor = new AnalysisVisitor(tree);
            tree.accept(visitor);
            return visitor.toAnalysis(runner.getOutVarNames(script), runner.getOutVarAttrs(script));
        } catch (QLSyntaxException | IllegalArgumentException error) {
            return QLScriptAnalysis.parseError("QL_PARSE_ERROR", "$", safeMessage(error));
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? "QL 脚本解析失败" : message;
    }

    private static final class AnalysisVisitor extends QLParserBaseVisitor<Void> {

        private final Deque<Set<String>> visibleScopes = new ArrayDeque<>();
        private final Map<String, Integer> localSymbolPositions = new LinkedHashMap<>();
        private final Map<String, String> localAssignments = new LinkedHashMap<>();
        private final Map<String, List<AssignmentExpression>> assignmentHistory =
                new LinkedHashMap<>();
        private final Set<String> topLevelAssignments = new LinkedHashSet<>();
        private final List<ReadCandidate> reads = new ArrayList<>();
        private final List<QLScriptAnalysis.Diagnostic> diagnostics = new ArrayList<>();
        private final Set<String> diagnosticKeys = new HashSet<>();
        private QLParser.ExpressionContext resultExpression;
        private int resultAssignmentPosition;
        private boolean explicitResult;

        private final Set<QLParser.ExpressionContext> topLevelExpressions =
                Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        private AnalysisVisitor(QLParser.ProgramContext tree) {
            visibleScopes.push(new LinkedHashSet<>());
            if (tree.blockStatements() != null) {
                for (QLParser.BlockStatementContext statement : tree.blockStatements().blockStatement()) {
                    if (statement instanceof QLParser.ExpressionStatementContext expression) {
                        topLevelExpressions.add(expression.expression());
                    }
                }
            }
        }

        @Override
        public Void visitExpression(QLParser.ExpressionContext ctx) {
            if (ctx.leftHandSide() == null || ctx.assignOperator() == null
                    || ctx.expression() == null) {
                return super.visitExpression(ctx);
            }

            QLParser.LeftHandSideContext left = ctx.leftHandSide();
            QLParser.ExpressionContext right = ctx.expression();
            String operator = ctx.assignOperator().getText();
            boolean plainAssignment = "=".equals(operator);
            if (!plainAssignment) {
                visitLeftHandSideRead(left);
            }
            right.accept(this);

            if (left.pathPart().isEmpty() && left.LPAREN() == null) {
                String name = left.varId().getText();
                int position = left.getStart().getStartIndex();
                addLocal(name, position);
                visibleScopes.peek().add(name);

                if (isTopLevelAssignment(ctx)) {
                    topLevelAssignments.add(name);
                    localAssignments.put(name, plainAssignment
                            ? normalize(right)
                            : effectiveCompoundExpression(left, operator, right));
                    assignmentHistory.computeIfAbsent(name, ignored -> new ArrayList<>())
                            .add(new AssignmentExpression(
                                    plainAssignment ? right : null, position));
                    if ("_result".equals(name)) {
                        explicitResult = true;
                        resultExpression = plainAssignment ? right : null;
                        resultAssignmentPosition = position;
                    }
                }
            } else if (plainAssignment) {
                visitPathExpressions(left.pathPart());
            }
            return null;
        }

        @Override
        public Void visitPrimary(QLParser.PrimaryContext ctx) {
            if (!(ctx.primaryNoFixPathable() instanceof QLParser.VarIdExprContext)) {
                return super.visitPrimary(ctx);
            }

            QLParser.VarIdExprContext var = (QLParser.VarIdExprContext) ctx.primaryNoFixPathable();
            if (var.LPAREN() != null) {
                if (var.argumentList() != null) {
                    var.argumentList().accept(this);
                }
                visitPathExpressions(ctx.pathPart());
                return null;
            }

            recordRead(var.varId().getText(), ctx.pathPart());
            return null;
        }

        private void visitLeftHandSideRead(QLParser.LeftHandSideContext left) {
            if (left.LPAREN() != null) {
                if (left.argumentList() != null) {
                    left.argumentList().accept(this);
                }
                visitPathExpressions(left.pathPart());
                return;
            }
            recordRead(left.varId().getText(), left.pathPart());
        }

        private void recordRead(String root, List<QLParser.PathPartContext> pathParts) {
            StringBuilder path = new StringBuilder(root);
            int unresolvedPathIndex = -1;
            for (int i = 0; i < pathParts.size(); i++) {
                QLParser.PathPartContext part = pathParts.get(i);
                if (part instanceof QLParser.FieldAccessContext) {
                    path.append('.').append(fieldName(
                            ((QLParser.FieldAccessContext) part).fieldId()));
                } else if (part instanceof QLParser.OptionalFieldAccessContext) {
                    path.append('.').append(fieldName(
                            ((QLParser.OptionalFieldAccessContext) part).fieldId()));
                } else if (part instanceof QLParser.SpreadFieldAccessContext) {
                    path.append('.').append(fieldName(
                            ((QLParser.SpreadFieldAccessContext) part).fieldId()));
                } else if (part instanceof QLParser.IndexExprContext) {
                    QLParser.IndexExprContext index = (QLParser.IndexExprContext) part;
                    String normalizedIndex = staticIndex(index.indexValueExpr());
                    if (normalizedIndex == null) {
                        unresolvedPathIndex = i;
                        addOpenShapeDiagnostic(path.toString(),
                                "动态索引无法静态确定完整属性路径");
                        break;
                    }
                    path.append('[').append(normalizedIndex).append(']');
                } else {
                    unresolvedPathIndex = i;
                    break;
                }
            }

            if (!isVisible(root)) {
                reads.add(new ReadCandidate(root, path.toString()));
            }
            if (unresolvedPathIndex >= 0) {
                visitPathExpressions(pathParts.subList(unresolvedPathIndex, pathParts.size()));
            }
        }

        @Override
        public Void visitLocalVariableDeclaration(QLParser.LocalVariableDeclarationContext ctx) {
            for (QLParser.VariableDeclaratorContext item
                    : ctx.variableDeclaratorList().variableDeclarator()) {
                if (item.variableInitializer() != null) {
                    item.variableInitializer().accept(this);
                }
                String name = item.variableDeclaratorId().varId().getText();
                addLocal(name, item.variableDeclaratorId().getStart().getStartIndex());
                visibleScopes.peek().add(name);
            }
            return null;
        }

        @Override
        public Void visitForEachStatement(QLParser.ForEachStatementContext ctx) {
            ctx.expression().accept(this);
            visibleScopes.push(new LinkedHashSet<>());
            try {
                String name = ctx.varId().getText();
                addLocal(name, ctx.varId().getStart().getStartIndex());
                visibleScopes.peek().add(name);
                if (ctx.blockStatements() != null) {
                    ctx.blockStatements().accept(this);
                }
            } finally {
                visibleScopes.pop();
            }
            return null;
        }

        @Override
        public Void visitFunctionStatement(QLParser.FunctionStatementContext ctx) {
            visibleScopes.push(new LinkedHashSet<>());
            try {
                if (ctx.formalOrInferredParameterList() != null) {
                    ctx.formalOrInferredParameterList().accept(this);
                }
                if (ctx.blockStatements() != null) {
                    ctx.blockStatements().accept(this);
                }
            } finally {
                visibleScopes.pop();
            }
            return null;
        }

        @Override
        public Void visitFormalOrInferredParameter(
                QLParser.FormalOrInferredParameterContext ctx) {
            String name = ctx.varId().getText();
            addLocal(name, ctx.varId().getStart().getStartIndex());
            visibleScopes.peek().add(name);
            return null;
        }

        private QLScriptAnalysis toAnalysis(Set<String> outVarNames,
                                            Set<List<String>> outVarAttrs) {
            Set<String> externalRoots = new HashSet<>(outVarNames);
            for (List<String> attrs : outVarAttrs) {
                if (!attrs.isEmpty()) {
                    externalRoots.add(attrs.get(0));
                }
            }

            Set<String> directInputs = new LinkedHashSet<>();
            for (ReadCandidate read : reads) {
                if (externalRoots.contains(read.root)
                        || localSymbolPositions.containsKey(read.root)) {
                    directInputs.add(read.path);
                }
            }

            List<String> localSymbols = new ArrayList<>(localSymbolPositions.keySet());
            localSymbols.sort(Comparator.comparingInt(localSymbolPositions::get));

            List<QLScriptAnalysis.OutputField> outputs = explicitResult
                    ? explicitOutputs()
                    : effectiveAssignmentOutputs();
            return new QLScriptAnalysis(
                    new ArrayList<>(directInputs),
                    localSymbols,
                    localAssignments,
                    outputs,
                    diagnostics,
                    explicitResult);
        }

        private List<QLScriptAnalysis.OutputField> explicitOutputs() {
            ResolvedMap resolvedMap = resolveMap(
                    resultExpression, resultAssignmentPosition, new HashSet<>());
            if (resolvedMap == null) {
                addOpenShapeDiagnostic("_result", "_result 无法静态解析为固定对象");
                return List.of();
            }
            QLParser.MapExprContext map = resolvedMap.map;
            if (map.mapEntries() == null) {
                return List.of();
            }

            List<QLScriptAnalysis.OutputField> outputs = new ArrayList<>();
            for (QLParser.MapEntryContext entry : map.mapEntries().mapEntry()) {
                outputs.add(new QLScriptAnalysis.OutputField(
                        mapKey(entry.mapKey()),
                        resolveExpressionAt(
                                entry.mapValue(),
                                resolvedMap.beforePosition,
                                new HashSet<>()),
                        null));
            }
            return outputs;
        }

        private List<QLScriptAnalysis.OutputField> effectiveAssignmentOutputs() {
            List<QLScriptAnalysis.OutputField> outputs = new ArrayList<>();
            for (String name : topLevelAssignments) {
                outputs.add(new QLScriptAnalysis.OutputField(
                        name, localAssignments.get(name), null));
            }
            return outputs;
        }

        private ResolvedMap resolveMap(QLParser.ExpressionContext expression,
                                       int beforePosition,
                                       Set<String> visited) {
            if (expression == null) {
                return null;
            }
            QLParser.MapExprContext directMap = soleDescendant(
                    expression, QLParser.MapExprContext.class);
            if (directMap != null) {
                return new ResolvedMap(directMap, beforePosition);
            }

            QLParser.VarIdExprContext directVar = soleDescendant(
                    expression, QLParser.VarIdExprContext.class);
            if (directVar == null || directVar.LPAREN() != null) {
                return null;
            }
            String name = directVar.varId().getText();
            AssignmentExpression assignment = latestAssignmentBefore(name, beforePosition);
            if (assignment == null || assignment.expression == null
                    || !visited.add(name + "@" + assignment.position)) {
                return null;
            }
            return resolveMap(assignment.expression, assignment.position, visited);
        }

        private String resolveExpressionAt(RuleContext expression,
                                           int beforePosition,
                                           Set<String> visited) {
            String normalized = normalize(expression);
            if (normalized == null
                    || !normalized.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return normalized;
            }
            AssignmentExpression assignment =
                    latestAssignmentBefore(normalized, beforePosition);
            if (assignment == null || assignment.expression == null) {
                return normalized;
            }
            String key = normalized + "@" + assignment.position;
            if (!visited.add(key)) {
                return normalized;
            }
            return resolveExpressionAt(
                    assignment.expression, assignment.position, visited);
        }

        private AssignmentExpression latestAssignmentBefore(String name, int beforePosition) {
            List<AssignmentExpression> assignments = assignmentHistory.get(name);
            if (assignments == null) {
                return null;
            }
            for (int i = assignments.size() - 1; i >= 0; i--) {
                AssignmentExpression assignment = assignments.get(i);
                if (assignment.position < beforePosition) {
                    return assignment;
                }
            }
            return null;
        }

        private <T extends RuleContext> T soleDescendant(
                RuleContext context, Class<T> type) {
            if (type.isInstance(context)) {
                return type.cast(context);
            }
            List<RuleContext> ruleChildren = new ArrayList<>();
            for (int i = 0; i < context.getChildCount(); i++) {
                ParseTree child = context.getChild(i);
                if (child instanceof RuleContext) {
                    ruleChildren.add((RuleContext) child);
                }
            }
            if (ruleChildren.size() != 1) {
                return null;
            }
            return soleDescendant(ruleChildren.get(0), type);
        }

        private void addLocal(String name, int position) {
            localSymbolPositions.putIfAbsent(name, position);
        }

        private boolean isVisible(String name) {
            for (Set<String> scope : visibleScopes) {
                if (scope.contains(name)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isTopLevelAssignment(QLParser.ExpressionContext context) {
            return topLevelExpressions.contains(context);
        }

        private void visitPathExpressions(List<QLParser.PathPartContext> parts) {
            for (QLParser.PathPartContext part : parts) {
                visitPathExpression(part);
            }
        }

        private void visitPathExpression(QLParser.PathPartContext part) {
            if (part instanceof QLParser.IndexExprContext) {
                ((QLParser.IndexExprContext) part).indexValueExpr().accept(this);
            } else if (part instanceof QLParser.MethodInvokeContext) {
                QLParser.ArgumentListContext args =
                        ((QLParser.MethodInvokeContext) part).argumentList();
                if (args != null) {
                    args.accept(this);
                }
            } else if (part instanceof QLParser.OptionalMethodInvokeContext) {
                QLParser.ArgumentListContext args =
                        ((QLParser.OptionalMethodInvokeContext) part).argumentList();
                if (args != null) {
                    args.accept(this);
                }
            } else if (part instanceof QLParser.SpreadMethodInvokeContext) {
                QLParser.ArgumentListContext args =
                        ((QLParser.SpreadMethodInvokeContext) part).argumentList();
                if (args != null) {
                    args.accept(this);
                }
            }
        }

        private void addOpenShapeDiagnostic(String path, String message) {
            String key = "OBJECT_SHAPE_INCOMPLETE|" + path;
            if (diagnosticKeys.add(key)) {
                diagnostics.add(new QLScriptAnalysis.Diagnostic(
                        "OBJECT_SHAPE_INCOMPLETE", "WARNING", path, message));
            }
        }

        private static String staticIndex(QLParser.IndexValueExprContext index) {
            if (!(index instanceof QLParser.SingleIndexContext)) {
                return null;
            }
            String text = ((QLParser.SingleIndexContext) index).expression().getText();
            if (text.matches("-?\\d+")
                    || (text.length() >= 2
                    && ((text.startsWith("\"") && text.endsWith("\""))
                    || (text.startsWith("'") && text.endsWith("'"))))) {
                return text;
            }
            return null;
        }

        private static String fieldName(QLParser.FieldIdContext field) {
            return unquote(field.getText());
        }

        private static String mapKey(QLParser.MapKeyContext key) {
            return unquote(key.getText());
        }

        private static String unquote(String text) {
            if (text != null && text.length() >= 2) {
                char first = text.charAt(0);
                char last = text.charAt(text.length() - 1);
                if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                    return text.substring(1, text.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\'", "'")
                            .replace("\\\\", "\\");
                }
            }
            return text;
        }

        private static String normalize(RuleContext context) {
            return context == null ? null : context.getText();
        }

        private static String effectiveCompoundExpression(
                QLParser.LeftHandSideContext left,
                String operator,
                QLParser.ExpressionContext right) {
            String binaryOperator = operator.substring(0, operator.length() - 1);
            return normalize(left) + binaryOperator + normalize(right);
        }
    }

    private static final class ResolvedMap {

        private final QLParser.MapExprContext map;
        private final int beforePosition;

        private ResolvedMap(QLParser.MapExprContext map, int beforePosition) {
            this.map = map;
            this.beforePosition = beforePosition;
        }
    }

    private static final class AssignmentExpression {

        private final QLParser.ExpressionContext expression;
        private final int position;

        private AssignmentExpression(QLParser.ExpressionContext expression, int position) {
            this.expression = expression;
            this.position = position;
        }
    }

    private static final class ReadCandidate {

        private final String root;
        private final String path;

        private ReadCandidate(String root, String path) {
            this.root = root;
            this.path = path;
        }
    }
}
