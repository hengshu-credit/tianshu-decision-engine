package com.hengshucredit.rule.server.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConsolePermissionCodes {

    private static final List<PermissionDefinition> CATALOG = buildCatalog();

    private ConsolePermissionCodes() {
    }

    public static List<PermissionDefinition> catalog() {
        return CATALOG;
    }

    public static Set<String> allCodes() {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (PermissionDefinition definition : CATALOG) {
            codes.add(definition.code());
        }
        return Collections.unmodifiableSet(codes);
    }

    private static List<PermissionDefinition> buildCatalog() {
        List<PermissionDefinition> definitions = new ArrayList<>();
        addGovernedResource(definitions, "field", "字段管理", "/variable", 100);
        addGovernedResource(definitions, "model", "模型管理", "/model", 200);
        addGovernedResource(definitions, "datasource", "外数管理", "/datasource", 300);
        addGovernedResource(definitions, "database", "数据库管理", "/database", 400);
        addGovernedResource(definitions, "function", "函数管理", "/function", 500);
        addGovernedResource(definitions, "rule", "规则管理", "/rule", 600);
        addGovernedResource(definitions, "experiment", "分流管理", "/experiment", 700);
        addGovernedResource(definitions, "project", "项目管理", "/project", 800);
        addGovernedResource(definitions, "list", "名单管理", "/list", 820);
        addGovernedResource(definitions, "billing", "账单管理", "/billing", 840);
        definitions.add(new PermissionDefinition(
                "log:view", "查看执行日志", "日志管理", "MENU", "/log", 860));
        definitions.add(new PermissionDefinition(
                "lineage:view", "查看血缘分析", "血缘分析", "MENU", "/lineage", 870));
        definitions.add(new PermissionDefinition(
                "transfer:view", "查看迁移记录和资源", "离线迁移", "MENU", "/transfer", 880));
        definitions.add(new PermissionDefinition(
                "transfer:export", "导出迁移包", "离线迁移", "ACTION", null, 881));
        definitions.add(new PermissionDefinition(
                "transfer:import", "导入迁移包", "离线迁移", "ACTION", null, 882));
        definitions.add(new PermissionDefinition(
                "approval:view", "查看审批", "审批管理", "MENU", "/approval", 900));
        definitions.add(new PermissionDefinition(
                "approval:submit", "提交审批", "审批管理", "ACTION", null, 901));
        definitions.add(new PermissionDefinition(
                "approval:approve", "审批处理", "审批管理", "ACTION", null, 902));
        definitions.add(new PermissionDefinition(
                "account:view", "查看账户", "账户管理", "MENU", "/account", 1000));
        definitions.add(new PermissionDefinition(
                "account:manage", "管理账户", "账户管理", "ACTION", null, 1001));
        definitions.add(new PermissionDefinition(
                "role:view", "查看角色", "账户管理", "ACTION", null, 1002));
        definitions.add(new PermissionDefinition(
                "role:manage", "管理角色", "账户管理", "ACTION", null, 1003));
        definitions.add(new PermissionDefinition(
                "account:audit", "查看操作日志", "账户管理", "ACTION", null, 1004));
        return Collections.unmodifiableList(definitions);
    }

    private static void addGovernedResource(List<PermissionDefinition> definitions,
                                            String code,
                                            String name,
                                            String menuPath,
                                            int order) {
        definitions.add(new PermissionDefinition(
                code + ":view", "查看" + name, name, "MENU", menuPath, order));
        definitions.add(new PermissionDefinition(
                code + ":edit", "编辑" + name, name, "ACTION", null, order + 1));
        definitions.add(new PermissionDefinition(
                code + ":submit", "提交" + name + "审批", name, "ACTION", null, order + 2));
    }

    public record PermissionDefinition(String code,
                                       String name,
                                       String group,
                                       String type,
                                       String menuPath,
                                       int sortOrder) {
    }
}
