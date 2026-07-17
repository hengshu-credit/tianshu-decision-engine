package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public void dockerFreshInitializationLoadsSchemaBeforeSnapshot() throws Exception {
        Path root = repositoryRoot();
        String rootCompose = read(root.resolve("docker-compose.yaml"));
        String mysqlCompose = read(root.resolve("rule-engine-mysql/docker-compose.yaml"));
        assertFreshInitMounts(rootCompose,
                "./rule-engine-server/src/main/resources/sql/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro",
                "./rule-engine-server/src/main/resources/sql/export_202607161151.sql:/docker-entrypoint-initdb.d/02-export.sql:ro");
        assertFreshInitMounts(mysqlCompose,
                "../rule-engine-server/src/main/resources/sql/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro",
                "../rule-engine-server/src/main/resources/sql/export_202607161151.sql:/docker-entrypoint-initdb.d/02-export.sql:ro");
        Assert.assertFalse("mysql-init must not replay destructive export",
                rootCompose.contains("export_202607161151.sql:/data.sql"));
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
