package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleDataObjectField;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Combine stored leaf aliases, object-relative import paths and legacy qualified paths using stable parent IDs. */
public final class DataObjectScriptPath {
    private DataObjectScriptPath() {}

    public static Map<Long, RuleDataObjectField> index(Collection<RuleDataObjectField> fields) {
        Map<Long, RuleDataObjectField> result = new HashMap<>();
        for (var field : fields) result.put(field.getId(), field);
        return result;
    }

    public static String resolve(String root, RuleDataObjectField leaf, Function<Long, RuleDataObjectField> parentById) {
        var chain = new ArrayDeque<RuleDataObjectField>();
        Set<Long> visited = new HashSet<>();
        for (var field = leaf; field != null; field = parentById.apply(field.getParentFieldId())) {
            if (!visited.add(field.getId()) || !Objects.equals(leaf.getObjectId(), field.getObjectId())) return null;
            chain.addFirst(field);
            if (field.getParentFieldId() == null) break;
        }
        String relative = "";
        String parentCodePath = "";
        for (var field : chain) {
            String code = field.getVarCode();
            String name = field.getScriptName();
            if (name == null || name.isBlank()) name = code;
            if (name == null || name.isBlank()) return null;
            if (!name.contains(".")) {
                relative = relative.isEmpty() ? name : relative + "." + name;
            } else if (!relative.isEmpty() && name.startsWith(relative + ".")) {
                relative = name;
            } else if (!parentCodePath.isEmpty() && name.startsWith(parentCodePath + ".")) {
                relative += name.substring(parentCodePath.length());
            } else if (root != null && name.startsWith(root + ".")) {
                relative = name.substring(root.length() + 1);
                // Older imports stored the qualified path in both varCode and scriptName.
                if (name.equals(code)) {
                    while (relative.startsWith(root + ".")) relative = relative.substring(root.length() + 1);
                }
            } else {
                relative = name;
            }
            String segment = code == null || code.isBlank() ? name : code;
            parentCodePath = parentCodePath.isEmpty() ? segment : parentCodePath + "." + segment;
        }
        return root == null || root.isBlank() ? relative : root + "." + relative;
    }
}
