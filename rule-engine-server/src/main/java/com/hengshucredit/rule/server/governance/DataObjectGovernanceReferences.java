package com.hengshucredit.rule.server.governance;

import com.hengshucredit.rule.server.mapper.RuleDataObjectFieldMapper;
import java.util.List;

/** Operand 引用字段ID，治理资源则是字段所属的数据对象ID。 */
final class DataObjectGovernanceReferences {
    private DataObjectGovernanceReferences() { }

    static List<ResourceDependencyRef> owners(List<ResourceDependencyRef> references, RuleDataObjectFieldMapper fields) {
        if (fields == null) return references;
        return references.stream().map(reference -> {
            String path = reference.referencePath();
            if (!GovernanceResourceTypes.DATA_OBJECT.equals(reference.targetResourceType()) || path == null
                    || !(path.endsWith(".refId") || path.endsWith(".varId"))) return reference;
            var field = fields.selectById(reference.targetResourceId());
            if (field == null || field.getObjectId() == null) return reference;
            return new ResourceDependencyRef(GovernanceResourceTypes.DATA_OBJECT, field.getObjectId(),
                    GovernanceResourceTypes.DATA_OBJECT, path, reference.relationType(), reference.required());
        }).toList();
    }
}
