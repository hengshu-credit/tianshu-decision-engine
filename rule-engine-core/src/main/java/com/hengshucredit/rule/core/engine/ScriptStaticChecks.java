package com.hengshucredit.rule.core.engine;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.aparser.QLParser;
import com.alibaba.qlexpress4.aparser.QLParserBaseVisitor;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Collect write targets at preparation; never scan source during execution. */
final class ScriptStaticChecks {
    private ScriptStaticChecks() { }

    static Set<String> assignmentRoots(Express4Runner runner, String script) {
        Set<String> roots = new LinkedHashSet<>();
        runner.parseToSyntaxTree(script).accept(new QLParserBaseVisitor<Void>() {
            @Override
            public Void visitLeftHandSide(QLParser.LeftHandSideContext target) {
                if (target.LPAREN() == null) roots.add(target.varId().getText());
                return super.visitLeftHandSide(target);
            }

            @Override
            public Void visitVariableDeclarator(QLParser.VariableDeclaratorContext declaration) {
                if (declaration.variableInitializer() != null) {
                    roots.add(declaration.variableDeclaratorId().varId().getText());
                }
                return super.visitVariableDeclarator(declaration);
            }

            @Override
            public Void visitPrimary(QLParser.PrimaryContext expression) {
                if (expression.primaryNoFixPathable() instanceof QLParser.VarIdExprContext variable
                        && variable.LPAREN() == null
                        && (expression.prefixExpress() != null
                            && increment(expression.prefixExpress().getText())
                        || expression.suffixExpress() != null
                            && increment(expression.suffixExpress().getText()))) {
                    roots.add(variable.varId().getText());
                }
                return super.visitPrimary(expression);
            }
        });
        return Collections.unmodifiableSet(roots);
    }

    private static boolean increment(String operator) {
        return "++".equals(operator) || "--".equals(operator);
    }

    static void assertDoesNotAssignConstants(Set<String> writes, Set<String> constants) {
        if (constants.isEmpty()) return;
        for (String root : writes) {
            if (constants.contains(root)) throw new IllegalStateException("常量字段不允许赋值: " + root);
        }
    }
}
