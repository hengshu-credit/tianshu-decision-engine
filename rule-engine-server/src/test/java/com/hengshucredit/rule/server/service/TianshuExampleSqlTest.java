package com.hengshucredit.rule.server.service;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.*;

public class TianshuExampleSqlTest {
    private static String sql() throws Exception {
        Path root = Files.isDirectory(Path.of("docker")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve("docker/rule-engine-mysql/data-tianshu-example.sql"));
    }

    @Test
    public void exportsPublishedDesignsAndRebuildsTheSignedModelInTheTarget() throws Exception {
        String sql = sql();
        assertEquals(20, sql.lines().filter("-- @build-artifact"::equals).count());
        assertEquals(1, sql.lines().filter("-- @freeze-model"::equals).count());
        for (String rule : List.of("CREDIT_ADMISSION", "JCZR", "NBQZ", "WBQZ", "SFDT", "SFSR", "SFMX",
                "ZXZR", "EDSX", "EDQS", "LLDJ", "DT_SCORE", "MOCK_PBOC")) assertTrue(sql.contains("-- " + rule + " V"));
        assertTrue(sql.contains("'scoreByBadRatePdo'"));
        assertTrue(sql.contains("@model_version_11"));
        assertFalse(sql.contains("'MAIN_A_INPUTS'"));
        assertFalse(sql.contains("v1:auto"));
        assertFalse(sql.contains("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    public void sourceQueriesAndListOperandsKeepTheirTargetIds() throws Exception {
        String sql = sql();
        var configs = sql.lines().filter(s -> s.startsWith("UPDATE rule_variable SET source_config=")).toList();
        assertTrue(configs.stream().anyMatch(s -> s.contains("applicant_id = ?") && s.contains("@db_datasource_1")
                && s.contains("@variable_20") && s.contains("@variable_21")));
        assertTrue(configs.stream().anyMatch(s -> s.contains("queryOperands") && s.contains("@list_library_3")));
        assertTrue(configs.stream().anyMatch(s -> s.contains("remotePath") && s.contains("@sftp_datasource_1") && s.contains("@variable_241")));
    }
}
