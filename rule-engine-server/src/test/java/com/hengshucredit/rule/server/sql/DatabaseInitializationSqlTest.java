package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DatabaseInitializationSqlTest {

    private static final Pattern DATA_MANIPULATION = Pattern.compile(
            "(?im)^\\s*(INSERT|DELETE|UPDATE|REPLACE)\\b");
    private static final Pattern INSERT = Pattern.compile(
            "(?im)^INSERT INTO\\s+(?:`?rule_engine`?\\.)?`?([a-z0-9_]+)`?\\s*\\(([^\\r\\n]+)\\)\\s+VALUES");
    private static final Pattern TRUNCATE = Pattern.compile(
            "(?im)^TRUNCATE TABLE\\s+(?:`?rule_engine`?\\.)?`?([a-z0-9_]+)`?\\s*;");
    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "(?is)CREATE TABLE IF NOT EXISTS\\s+`([^`]+)`\\s*\\((.*?)\\)\\s*ENGINE=");
    private static final Pattern MOJIBAKE = Pattern.compile(
            "(?:Ã[\\u0080-\\u024F]|Â[\\u0080-\\u024F]|"
                    + "â[\\u0080-\\u024F\\u2000-\\u20FF]|"
                    + "[åæçèéä][\\u0080-\\u024F\\u2000-\\u20FF]|"
                    + "�|锟斤拷|烫烫烫|屯屯屯)");
    private static final List<String> CANONICAL_CONSTANTS = Arrays.asList(
            "NULL_VALUE", "EMPTY_STRING", "EMPTY_LIST", "EMPTY_MAP",
            "TRUE_VALUE", "FALSE_VALUE", "ZERO", "ONE", "NEGATIVE_ONE",
            "POSITIVE_INFINITY", "NEGATIVE_INFINITY");
    private static final List<String> OBSOLETE_CONSTANTS = Arrays.asList(
            "EMPTY_OBJECT", "NULL_STRING", "NULL_NUMBER", "NULL_OBJECT", "NULL_LIST", "NULL_MAP");

    @Test
    public void schemaContainsNoDataManipulationStatements() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        String withoutComments = schema
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");
        Matcher matcher = DATA_MANIPULATION.matcher(withoutComments);
        Assert.assertFalse("schema.sql contains data statement: "
                + (matcher.find() ? matcher.group(1) : ""), matcher.reset().find());
    }

    @Test
    public void latestExportIsRerunnableFullSnapshot() throws Exception {
        String export = read(latestExport());
        Set<String> insertTables = collectTables(INSERT, export);
        Set<String> truncateTables = collectTables(TRUNCATE, export);
        Assert.assertFalse("export must contain INSERT targets", insertTables.isEmpty());
        Assert.assertTrue("every INSERT target must be truncated before restore",
                truncateTables.containsAll(insertTables));
        Assert.assertTrue(export.contains("SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS"));
        Assert.assertTrue(export.contains("SET FOREIGN_KEY_CHECKS = 0"));
        Assert.assertTrue(export.contains("SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS"));
        Assert.assertFalse(export.toUpperCase().contains("INSERT IGNORE"));
    }

    @Test
    public void latestExportDeclaresUtf8mb4BeforeRestoringData() throws Exception {
        String export = read(latestExport());
        int setNames = export.indexOf("SET NAMES utf8mb4");
        int setConnection = export.indexOf("SET character_set_connection = utf8mb4");
        int firstTruncate = export.indexOf("TRUNCATE TABLE");
        int firstInsert = export.indexOf("INSERT INTO");
        int firstDataStatement = Math.min(firstTruncate, firstInsert);

        Assert.assertTrue("export must declare SET NAMES utf8mb4", setNames >= 0);
        Assert.assertTrue("export must set the connection character set to utf8mb4",
                setConnection >= 0);
        Assert.assertTrue("UTF-8 connection settings must precede all restored data",
                setNames < firstDataStatement && setConnection < firstDataStatement);
    }

    @Test
    public void canonicalInitializationSqlContainsNoMojibake() throws Exception {
        for (Path sql : Arrays.asList(sqlDirectory().resolve("schema.sql"), latestExport())) {
            Matcher matcher = MOJIBAKE.matcher(read(sql));
            Assert.assertFalse(sql.getFileName() + " contains mojibake near "
                    + (matcher.find() ? matcher.group() : ""), matcher.reset().find());
        }
    }

    @Test
    public void latestExportPreservesEveryExportedAutoIncrementId() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        String export = read(latestExport());
        Set<String> autoIncrementTables = new HashSet<>();
        Matcher tableMatcher = TABLE_BLOCK.matcher(schema);
        while (tableMatcher.find()) {
            if (tableMatcher.group(2).toUpperCase().contains("AUTO_INCREMENT")) {
                autoIncrementTables.add(tableMatcher.group(1));
            }
        }

        Map<String, Integer> insertCount = new HashMap<>();
        Matcher insertMatcher = INSERT.matcher(export);
        while (insertMatcher.find()) {
            String table = insertMatcher.group(1);
            insertCount.put(table, insertCount.containsKey(table) ? insertCount.get(table) + 1 : 1);
            if (autoIncrementTables.contains(table)) {
                List<String> columns = Arrays.asList(insertMatcher.group(2)
                        .replace("`", "")
                        .replace(" ", "")
                        .split(","));
                Assert.assertTrue(table + " INSERT must include id", columns.contains("id"));
            }
        }
        Assert.assertFalse("export INSERT statements were not parsed", insertCount.isEmpty());
    }

    @Test
    public void schemaDoesNotCreateOrAlterDatabaseAccounts() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql")).toUpperCase();
        Assert.assertFalse(schema.contains("CREATE USER"));
        Assert.assertFalse(schema.contains("ALTER USER"));
        Assert.assertFalse(schema.contains("GRANT ALL PRIVILEGES"));
    }

    @Test
    public void latestExportDoesNotPersistEnvironmentBoundProjectAuthenticationData() throws Exception {
        String export = read(latestExport());
        Set<String> insertTables = collectTables(INSERT, export);
        Set<String> truncateTables = collectTables(TRUNCATE, export);
        List<String> environmentBoundTables = Arrays.asList(
                "rule_project_auth", "rule_project_auth_token", "rule_auth_access_log");

        for (String table : environmentBoundTables) {
            Assert.assertFalse(table + " must not contain environment-bound snapshot data",
                    insertTables.contains(table));
            Assert.assertTrue(table + " must still be cleared when restoring the snapshot",
                    truncateTables.contains(table));
        }
    }

    @Test
    public void latestExportContainsCanonicalConstantsAtStableIds() throws Exception {
        String export = read(latestExport());
        for (String code : CANONICAL_CONSTANTS) {
            Assert.assertTrue("export missing canonical constant " + code, export.contains("'" + code + "'"));
        }
        for (String code : OBSOLETE_CONSTANTS) {
            Pattern obsoleteRow = Pattern.compile(
                    "\\(\\d+\\s*,\\s*0\\s*,\\s*'GLOBAL'\\s*,\\s*'" + code + "'");
            Assert.assertFalse("export contains obsolete constant row " + code,
                    obsoleteRow.matcher(export).find());
        }
        assertVariableId(export, 204L, "PASS");
        assertVariableId(export, 206L, "hit_ruleset");
        assertVariableId(export, 209L, "EMPTY_MAP");
    }

    @Test
    public void seededDefinitionsHaveACompleteDraftOrPublishedExecutionChain() throws Exception {
        String export = read(latestExport());
        List<List<String>> definitions = insertRows(export, "rule_definition");
        List<List<String>> contents = insertRows(export, "rule_definition_content");
        List<List<String>> versions = insertRows(export, "rule_definition_version");
        List<List<String>> publishedRules = insertRows(export, "rule_published");
        Set<Long> contentDefinitionIds = new HashSet<>();
        Set<String> versionKeys = new HashSet<>();
        Set<String> publishedKeys = new HashSet<>();

        for (List<String> row : contents) {
            contentDefinitionIds.add(Long.valueOf(row.get(1)));
        }
        for (List<String> row : versions) {
            versionKeys.add(row.get(1) + ":" + row.get(2));
        }
        for (List<String> row : publishedRules) {
            publishedKeys.add(row.get(2) + ":" + row.get(4));
        }

        for (List<String> definition : definitions) {
            Long definitionId = Long.valueOf(definition.get(0));
            String ruleCode = sqlString(definition.get(4));
            Assert.assertTrue(ruleCode + " must have editable content",
                    contentDefinitionIds.contains(definitionId));
            Assert.assertTrue(ruleCode + " current_version must be positive",
                    Long.parseLong(definition.get(9)) > 0L);
            if (!"NULL".equals(definition.get(10))) {
                String publishedKey = definitionId + ":" + definition.get(10);
                Assert.assertTrue(ruleCode + " published_version must have an immutable revision",
                        versionKeys.contains(publishedKey));
                Assert.assertTrue(ruleCode + " published_version must have a runtime artifact",
                        publishedKeys.contains(publishedKey));
            }
        }
    }

    @Test
    public void seededDefinitionDraftsAndReferencesMatchTheirActualDefinitions() throws Exception {
        String export = read(latestExport());
        List<List<String>> definitions = insertRows(export, "rule_definition");
        List<List<String>> contents = insertRows(export, "rule_definition_content");
        Set<Long> definitionIds = new HashSet<>();
        Map<String, Long> definitionIdByCode = new HashMap<>();
        Map<Long, List<String>> contentByDefinitionId = new HashMap<>();

        for (List<String> definition : definitions) {
            Long definitionId = Long.valueOf(definition.get(0));
            definitionIds.add(definitionId);
            definitionIdByCode.put(sqlString(definition.get(4)), definitionId);
        }
        for (List<String> content : contents) {
            contentByDefinitionId.put(Long.valueOf(content.get(1)), content);
        }

        Assert.assertEquals("editable drafts must not be missing or orphaned",
                definitionIds, contentByDefinitionId.keySet());
        assertDefinitionReferenceIds(export, "rule_definition_input_field", definitionIds);
        assertDefinitionReferenceIds(export, "rule_definition_output_field", definitionIds);
        assertDefinitionReferenceIds(export, "rule_definition_ref", definitionIds);

        assertDraftOwner(definitionIdByCode, contentByDefinitionId,
                "SXCL0001", "\"nodes\"", "credit_limit");
        assertDraftOwner(definitionIdByCode, contentByDefinitionId,
                "MONTHLY_SUCCESSFUL_REPAYMENT_AMOUNT", "\"rowDimensions\"",
                "monthly_successful_repayment_amount");
        assertDraftOwner(definitionIdByCode, contentByDefinitionId,
                "RISK_FACTOR", "\"rowDimensions\"", "risk_factor");
        assertDraftOwner(definitionIdByCode, contentByDefinitionId,
                "RISK_LIMIT", "\"rowDimensions\"", "risk_limit");
        assertDraftOwner(definitionIdByCode, contentByDefinitionId,
                "AMOUNT_FORMULA", "\"script\"", "numCeil");
        assertDraftOwner(definitionIdByCode, contentByDefinitionId,
                "CREDIT_AMOUNT", "\"nodes\"", "executeRuleById");
    }

    @Test
    public void sxedSnapshotsUseTheDisplayedLowerBoundWithoutOverlap() throws Exception {
        String export = read(latestExport());
        List<List<String>> snapshots = new ArrayList<>();
        for (List<String> row : insertRows(export, "rule_definition_content")) {
            if ("7".equals(row.get(1))) snapshots.add(Arrays.asList(row.get(2), row.get(3)));
        }
        for (List<String> row : insertRows(export, "rule_definition_version")) {
            if ("7".equals(row.get(1))) snapshots.add(Arrays.asList(row.get(3), row.get(4)));
        }
        for (List<String> row : insertRows(export, "rule_published")) {
            if ("7".equals(row.get(2))) snapshots.add(Arrays.asList(row.get(8), row.get(6)));
        }

        Assert.assertEquals("SXED content, revision and published artifact must all be checked", 3,
                snapshots.size());
        for (List<String> snapshot : snapshots) {
            String modelJson = sqlString(snapshot.get(0));
            String compiledScript = sqlString(snapshot.get(1));
            Assert.assertTrue(modelJson.contains(
                    "\"label\":\"[250, 350)\",\"operator\":\"range\",\"value\":\"\","
                            + "\"min\":\"250\",\"max\":\"350\""));
            Assert.assertFalse(modelJson.contains("\"min\":\"205\""));
            Assert.assertEquals("each age segment must use the same SXED score lower bound", 4,
                    occurrences(compiledScript, "score_f1.score >= 250"));
            Assert.assertFalse(compiledScript.contains("score_f1.score >= 205"));
        }
    }

    @Test
    public void dockerFreshInitializationLoadsSchemaBeforeSnapshot() throws Exception {
        Path root = repositoryRoot();
        String rootCompose = read(root.resolve("docker/docker-compose.yml"));
        String mysqlCompose = read(root.resolve("docker/docker-compose.mysql.yml"));
        assertFreshInitMounts(mysqlCompose,
                "../rule-engine-server/src/main/resources/sql/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro",
                "../rule-engine-server/src/main/resources/sql/export_202607161151.sql:/docker-entrypoint-initdb.d/02-export.sql:ro");
        Assert.assertTrue("main compose must run schema initialization before application services",
                rootCompose.contains("mysql-init:") && rootCompose.contains("condition: service_completed_successfully"));
        Assert.assertTrue("snapshot loading must be opt-in for an existing database",
                rootCompose.contains("MYSQL_INIT_LOAD_SNAPSHOT") && rootCompose.contains("/init/export.sql"));
    }

    @Test
    public void realOnnxAssetsAreOnlyEnabledByTheExplicitIntegrationProfile() throws Exception {
        String pom = read(repositoryRoot().resolve("rule-engine-server/pom.xml"));

        Assert.assertEquals("the ignored repository asset directory must not be a default test resource", 1,
                occurrences(pom, "${project.basedir}/../assets"));
        Assert.assertTrue(pom.contains("<id>onnx-integration</id>"));
        Assert.assertTrue(pom.contains("<tianshu.onnx.integration>true</tianshu.onnx.integration>"));
        Assert.assertTrue(pom.contains("<tianshu.onnx.integration>"
                + "${tianshu.onnx.integration}</tianshu.onnx.integration>"));
    }

    @Test
    public void schemaCreatesConsoleUserPreferenceTable() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Matcher matcher = TABLE_BLOCK.matcher(schema);
        String tableBody = null;
        while (matcher.find()) {
            if ("console_user_preference".equals(matcher.group(1))) {
                tableBody = matcher.group(2);
                break;
            }
        }

        Assert.assertNotNull("console_user_preference table missing", tableBody);
        Assert.assertTrue(tableBody.matches(
                "(?is).*`preference_value`\\s+JSON\\s+NOT NULL.*"));
        Assert.assertTrue(schema.contains(
                "UNIQUE KEY `uk_console_user_preference` (`user_id`, `preference_key`)"));
        Assert.assertTrue(schema.contains(
                "KEY `idx_console_user_preference_user` (`user_id`)"));
        String export = read(latestExport());
        Assert.assertTrue(export.contains(
                "TRUNCATE TABLE rule_engine.console_user_preference;"));
        Assert.assertFalse(export.contains(
                "INSERT INTO rule_engine.`console_user_preference`"));
    }

    @Test
    public void executionLogSupportsLargeJsonPayloadsAndUpgradesExistingSchema() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Matcher matcher = TABLE_BLOCK.matcher(schema);
        String tableBody = null;
        while (matcher.find()) {
            if ("rule_execution_log".equals(matcher.group(1))) {
                tableBody = matcher.group(2);
                break;
            }
        }

        Assert.assertNotNull("rule_execution_log table missing", tableBody);
        Assert.assertTrue(tableBody.matches("(?is).*`input_params`\\s+LONGTEXT.*"));
        Assert.assertTrue(tableBody.matches("(?is).*`output_result`\\s+LONGTEXT.*"));
        Assert.assertTrue(schema.contains("MODIFY COLUMN `input_params` LONGTEXT"));
        Assert.assertTrue(schema.contains("MODIFY COLUMN `output_result` LONGTEXT"));
    }

    @Test
    public void schemaCreatesApiDocumentationScenarioTable() throws Exception {
        String schema = read(sqlDirectory().resolve("schema.sql"));
        Matcher matcher = TABLE_BLOCK.matcher(schema);
        String tableBody = null;
        while (matcher.find()) {
            if ("rule_api_doc_scenario".equals(matcher.group(1))) {
                tableBody = matcher.group(2);
                break;
            }
        }

        Assert.assertNotNull("rule_api_doc_scenario table missing", tableBody);
        Assert.assertTrue(tableBody.matches("(?is).*`request_json`\\s+LONGTEXT\\s+NOT NULL.*"));
        Assert.assertTrue(tableBody.matches("(?is).*`response_json`\\s+LONGTEXT\\s+NOT NULL.*"));
        Assert.assertTrue(schema.contains(
                "UNIQUE KEY `uk_api_doc_scenario_name` (`definition_id`, `scenario_name`)"));
        Assert.assertTrue(schema.contains(
                "KEY `idx_api_doc_scenario_export` (`definition_id`, `status`, `include_in_doc`, `sort_order`)"));
        Assert.assertTrue(schema.contains("MODIFY COLUMN `request_json` LONGTEXT NOT NULL"));
        Assert.assertTrue(schema.contains("MODIFY COLUMN `response_json` LONGTEXT NOT NULL"));
    }

    private static void assertFreshInitMounts(String compose, String schemaMount, String exportMount) {
        Assert.assertTrue("missing schema init mount", compose.contains(schemaMount));
        Assert.assertTrue("missing export init mount", compose.contains(exportMount));
        Assert.assertTrue("schema must be mounted before export",
                compose.indexOf(schemaMount) < compose.indexOf(exportMount));
    }

    private static void assertVariableId(String export, long id, String code) {
        Pattern row = Pattern.compile("\\(" + id
                + "\\s*,\\s*0\\s*,\\s*'GLOBAL'\\s*,\\s*'" + code + "'");
        Assert.assertTrue(code + " must keep id " + id, row.matcher(export).find());
    }

    private static void assertDefinitionReferenceIds(String export, String table,
                                                     Set<Long> definitionIds) {
        for (List<String> row : insertRows(export, table)) {
            Assert.assertTrue(table + " contains orphan definition_id=" + row.get(1),
                    definitionIds.contains(Long.valueOf(row.get(1))));
        }
    }

    private static void assertDraftOwner(Map<String, Long> definitionIdByCode,
                                         Map<Long, List<String>> contentByDefinitionId,
                                         String ruleCode, String modelMarker,
                                         String scriptMarker) {
        Long definitionId = definitionIdByCode.get(ruleCode);
        Assert.assertNotNull("missing definition " + ruleCode, definitionId);
        List<String> content = contentByDefinitionId.get(definitionId);
        Assert.assertNotNull("missing editable draft for " + ruleCode, content);
        Assert.assertTrue(ruleCode + " has another rule's model",
                sqlString(content.get(2)).contains(modelMarker));
        Assert.assertTrue(ruleCode + " has another rule's compiled script",
                sqlString(content.get(3)).contains(scriptMarker));
    }

    private static List<List<String>> insertRows(String export, String table) {
        Pattern statement = Pattern.compile("(?m)^INSERT INTO\\s+(?:`?rule_engine`?\\.)?`?"
                + Pattern.quote(table) + "`?\\s*\\([^\\r\\n]+\\)\\s+VALUES\\s+(.*);$");
        Matcher matcher = statement.matcher(export);
        List<List<String>> rows = new ArrayList<>();
        while (matcher.find()) {
            for (String row : splitTopLevel(matcher.group(1))) {
                Assert.assertTrue(table + " contains a malformed row", row.startsWith("(")
                        && row.endsWith(")"));
                rows.add(splitTopLevel(row.substring(1, row.length() - 1)));
            }
        }
        Assert.assertFalse("missing INSERT for " + table, rows.isEmpty());
        return rows;
    }

    private static List<String> splitTopLevel(String source) {
        List<String> values = new ArrayList<>();
        int start = 0;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '\'') {
                    quoted = false;
                }
            } else if (current == '\'') {
                quoted = true;
            } else if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                values.add(source.substring(start, i).trim());
                start = i + 1;
            }
        }
        values.add(source.substring(start).trim());
        return values;
    }

    private static String sqlString(String literal) {
        Assert.assertTrue("expected SQL string literal", literal.length() >= 2
                && literal.charAt(0) == '\'' && literal.charAt(literal.length() - 1) == '\'');
        String value = literal.substring(1, literal.length() - 1);
        StringBuilder decoded = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaped && current == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                if (current == 'n') current = '\n';
                else if (current == 'r') current = '\r';
                else if (current == 't') current = '\t';
                else if (current == 'b') current = '\b';
                else if (current == '0') current = '\0';
                escaped = false;
            }
            decoded.append(current);
        }
        if (escaped) decoded.append('\\');
        return decoded.toString();
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Set<String> collectTables(Pattern pattern, String sql) {
        Set<String> tables = new HashSet<>();
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private static Path latestExport() throws Exception {
        try (Stream<Path> paths = Files.list(sqlDirectory())) {
            return paths
                    .filter(path -> path.getFileName().toString().matches("export_\\d{12}\\.sql"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new AssertionError("No timestamped export SQL found"));
        }
    }

    private static Path sqlDirectory() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path modulePath = cwd.resolve("src/main/resources/sql");
        if (Files.isDirectory(modulePath)) {
            return modulePath;
        }
        return cwd.resolve("rule-engine-server/src/main/resources/sql");
    }

    private static Path repositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
