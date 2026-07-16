package com.hengshucredit.rule.server.sql;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseInitializationSqlTest {

    private static final Pattern DATA_MANIPULATION = Pattern.compile(
            "(?im)^\\s*(INSERT|DELETE|UPDATE|REPLACE)\\b");

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

    private static Path sqlDirectory() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path modulePath = cwd.resolve("src/main/resources/sql");
        if (Files.isDirectory(modulePath)) {
            return modulePath;
        }
        return cwd.resolve("rule-engine-server/src/main/resources/sql");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
