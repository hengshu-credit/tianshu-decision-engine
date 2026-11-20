package com.hengshucredit.rule.server.service;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;
import com.hengshucredit.rule.core.pmml.PMMLModelExecutor;
import com.hengshucredit.rule.core.function.DecisionBuiltinFunctions;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import static org.junit.Assert.*;

public class TianshuExampleSqlTest {
    @Test
    public void compressedMainAContainsThePublishedPmmlAndReproducesBothScores() throws Exception {
        String sql = sql();
        var data = Pattern.compile("SET @main_a_original = UNCOMPRESS\\(FROM_BASE64\\('([A-Za-z0-9+/=]+)'\\)\\);").matcher(sql);
        assertTrue("missing portable PMML", data.find());
        byte[] stored = Base64.getDecoder().decode(data.group(1));
        assertFalse("model payload must occur only once", data.find());
        assertEquals(232796, ByteBuffer.wrap(stored).order(ByteOrder.LITTLE_ENDIAN).getInt());
        try (var compressed = new InflaterInputStream(new ByteArrayInputStream(stored, 4, stored.length - 4))) {
            byte[] pmml = compressed.readNBytes(232797);
            assertEquals(232796, pmml.length);
            assertEquals("3b314bb47df0cd17342e12b6e0a1219988d4dace0e1ab1991fa34aeea700bddb", Sha256Digests.bytes(pmml));
            var evaluator = new PMMLModelExecutor();
            assertEquals(594.02, mainAScore(evaluator, pmml, 10000, 35, 550), 0.001);
            assertEquals(-33.90, mainAScore(evaluator, pmml, 1000, 88, 301), 0.001);
        }
    }

    private double mainAScore(PMMLModelExecutor evaluator, byte[] pmml, int income, int fraud, int credit) {
        var result = evaluator.evaluateBytes(pmml, Map.of("multi_1m", 2, "multi_3m", 4, "multi_6m", 7,
                "multi_12m", 13, "verified_income", income, "fraud_score", fraud, "credit_score", credit));
        var probability = (Number) result.get("main_a_score");
        return new DecisionBuiltinFunctions().scoreByBadRatePdo(probability.doubleValue(), 600, null, 0.1, 2, 50, "ASC").doubleValue();
    }
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
