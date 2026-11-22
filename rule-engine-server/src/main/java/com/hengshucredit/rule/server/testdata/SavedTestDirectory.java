package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.server.security.ConsoleDataPermissionHandler;
import com.hengshucredit.rule.server.security.ConsoleProjectAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 用例目录直接投影各模块保存的记录，更新、删除和项目授权即时生效。 */
@Service
@RequiredArgsConstructor
class SavedTestDirectory {
    private final JdbcTemplate jdbc;
    private final ConsoleDataPermissionHandler permissions;
    private final ConsoleProjectAccess projects;

    Map<String,Object> list(TestResource filter, String keyword, int page, String scope, Long projectId) {
        String union = query(filter,false);
        String where = " WHERE (name LIKE ? OR resourceName LIKE ? OR code LIKE ?)";
        String search = "%"+keyword.trim()+"%";
        List<Object> args = new ArrayList<>(List.of(search,search,search));
        if (scope != null && !scope.isBlank()) { where += " AND scope=?"; args.add(scope); }
        if (projectId != null) { where += " AND projectId=?"; args.add(projectId); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)"+union+where, Long.class,args.toArray());
        args.add(20L*(Math.max(1,page)-1));
        var rows = jdbc.queryForList("SELECT *"+union+where+" ORDER BY source='DEFAULT',type,ownerId DESC,source,id LIMIT 20 OFFSET ?",args.toArray());
        return Map.of("records",rows,"total",total == null ? 0 : total);
    }

    String sample(TestResource type,String source,long owner,String id) {
        var rows = jdbc.queryForList("SELECT paramsJson"+query(type,true)+" WHERE source=? AND ownerId=? AND id=?",
                String.class,source,owner,id);
        if (rows.size()!=1 || rows.get(0)==null) throw new IllegalArgumentException("样例已移除或当前范围不可访问");
        return rows.get(0);
    }

    private String query(TestResource filter,boolean includeSample) {
        List<String> sources = new ArrayList<>();
        for (TestResource type : TestResource.values()) {
            if (filter != null && filter != type) continue;
            sources.add(source(includeSample,type, "CASE", "c.id", type == TestResource.RULE ? "c.scenario_name" : "c.case_name",
                    "JOIN "+type.cases+" c ON c."+type.owner+"=r.id", "1=1"));
            String defaults = switch (type) {
                case RULE -> "1=1";
                case VARIABLE -> "r.var_source IN ('API','DB','SFTP','LIST','DERIVED')";
                case MODEL -> "NOT COALESCE(("+hasSample("r.model_config","testParams")+"),FALSE)";
                case API -> "r.test_sample_params IS NULL OR JSON_TYPE(r.test_sample_params)<>'OBJECT'";
            };
            sources.add(source(includeSample,type,"DEFAULT","r.id","'默认测试输入'","",defaults));
            switch (type) {
                case MODEL -> {
                    sources.add(source(includeSample,type,"MODEL_SAMPLE","r.id","'模型测试参数'","",hasSample("r.model_config","testParams")));
                    sources.add(source(includeSample,type,"MODEL_VERSION","v.version","CONCAT('模型 v',v.version,' 测试参数')",
                            "JOIN rule_model_version v ON v.model_id=r.id",hasSample("v.model_config","testParams")+" AND ("+permissions.predicate("rule_model_version","v")+")"));
                }
                case API -> {
                    sources.add(source(includeSample,type,"API_SAMPLE","r.id","'接口测试样例'","","JSON_TYPE(r.test_sample_params)='OBJECT'"));
                    sources.add(source(includeSample,type,"API_NAMED","s.sampleId","s.sampleName",
                            "JOIN JSON_TABLE(r.execution_config,'$.samples[*]' COLUMNS(idx FOR ORDINALITY,sampleId VARCHAR(128) PATH '$.id',"
                                    +"sampleName VARCHAR(256) PATH '$.name')) s ON TRUE","s.sampleId IS NOT NULL"));
                }
                case RULE -> {
                    sources.add(source(includeSample,type,"DESIGN","r.id","'评分卡工作区样例'",
                            "JOIN rule_definition_content d ON d.definition_id=r.id", "r.model_type='SCORE' AND "+hasSample("d.model_json","testParams")));
                    var scope = projects.current();
                    String owner = scope == null || scope.globalCan("account:cross-record-view") ? "1=1"
                            : "v.actor_user_id="+scope.userId();
                    sources.add(source(includeSample,type,"REVISION","v.id","CONCAT('评分卡修订 ',v.revision_no,' 样例')",
                            "JOIN rule_revision v ON v.definition_id=r.id","r.model_type='SCORE' AND v.state<>'DELETED' AND ("
                                    +owner+") AND "+hasSample("v.model_json","testParams")));
                }
                default -> { }
            }
        }
        List<String> drafts = new ArrayList<>();
        for (TestResource type : List.of(TestResource.API,TestResource.MODEL)) {
            if ((filter == null || filter == type) && useWorkspace(type)) drafts.add(workspace(type));
        }
        String with = drafts.isEmpty() ? "" : "WITH "+String.join(",",drafts)+" ";
        return " FROM ("+with+String.join(" UNION ALL ",sources)+") entries";
    }

    private boolean useWorkspace(TestResource type) {
        if (type != TestResource.API && type != TestResource.MODEL) return false;
        var scope = projects.current();
        return scope != null && scope.userId() != null && scope.can(type == TestResource.API ? "datasource:edit" : "model:edit");
    }

    // 与模块编辑入口一致：只合并当前账户有权编辑的活动草稿，已提交/历史版本按生效记录展示。
    private String workspace(TestResource type) {
        String projection = type == TestResource.API
                ? "r.datasource_id,r.api_name,r.api_code,"+draftField("execution_config","executionConfig")+","+draftField("test_sample_params","testSampleParams")
                : "r.scope,r.project_id,r.model_name,r.model_code,"+draftField("model_config","modelConfig");
        return "workspace_"+type+" AS (SELECT r.id,r.status,"+projection+" FROM "+type.table+" r "
                +"LEFT JOIN governance_approval_request g ON g.active_resource_key=CONCAT('"+type.accessType+":',r.id)"
                +" AND g.status='EDITING' AND g.actor_user_id="+projects.current().userId()
                +" AND ("+permissions.forMutation(() -> permissions.predicate(type.table,"r"))+")"
                // MySQL 8 的 JSON_TABLE 需要已物化的单一来源列；禁止合并含草稿和生效配置的派生表。
                +" WHERE r.status>=0 AND ("+permissions.predicate(type.table,"r")+") LIMIT 18446744073709551615)";
    }

    private String draftField(String column,String key) {
        String field = "JSON_EXTRACT(g.draft_snapshot_json,'$."+key+"')";
        return "CASE WHEN g.id IS NULL OR "+field+" IS NULL THEN r."+column
                +" WHEN JSON_TYPE("+field+")='NULL' THEN NULL ELSE JSON_UNQUOTE("+field+") END AS "+column;
    }
    private String source(boolean includeSample,TestResource type,String source,String id,String name,String join,String filter) {
        String payload = !includeSample ? "" : ","+switch (source) {
            case "MODEL_SAMPLE" -> "JSON_UNQUOTE(JSON_EXTRACT(r.model_config,'$.testParams'))";
            case "MODEL_VERSION" -> "JSON_UNQUOTE(JSON_EXTRACT(v.model_config,'$.testParams'))";
            case "DESIGN" -> "JSON_UNQUOTE(JSON_EXTRACT(d.model_json,'$.testParams'))";
            case "REVISION" -> "JSON_UNQUOTE(JSON_EXTRACT(v.model_json,'$.testParams'))";
            case "API_SAMPLE" -> "CAST(r.test_sample_params AS CHAR CHARACTER SET utf8mb4)";
            case "API_NAMED" -> "JSON_EXTRACT(r.execution_config,CONCAT('$.samples[',s.idx-1,'].params'))";
            default -> "NULL";
        }+" AS paramsJson";
        String owner = type == TestResource.API ? "ds" : "r";
        String parent = type == TestResource.API ? " JOIN rule_external_datasource ds ON ds.id=r.datasource_id AND ds.status>=0" : "";
        return "SELECT '"+type+"' AS type,'"+source+"' AS source, CAST("+id+" AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci AS id,"
                +name+" COLLATE utf8mb4_unicode_ci AS name,r.id AS ownerId,r."+type.label+" AS resourceName,r."+type.code+" AS code,"
                +owner+".scope AS scope,"+owner+".project_id AS projectId,p.project_name AS projectName"+payload
                +" FROM "+(useWorkspace(type) ? "workspace_"+type : type.table)+" r "+parent+" LEFT JOIN rule_project p ON p.id="+owner+".project_id "+join+" WHERE r.status>=0 AND ("+permissions.predicate(type.table,"r")+") AND ("+filter+")";
    }
    private String hasSample(String column,String key) {
        String value = "JSON_EXTRACT(IF(JSON_VALID("+column+"),"+column+",NULL),'$."+key+"')";
        return "JSON_TYPE("+value+") IN ('OBJECT','STRING') AND JSON_UNQUOTE("+value+")<>''";
    }
}
