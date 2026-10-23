package com.hengshucredit.rule.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads the single-line dotenv settings shared by source, JAR and Compose deployments. */
public final class LocalEnvironmentProperties {
    private LocalEnvironmentProperties() {
    }

    public static Map<String, Object> load(Path directory, String serviceFile) {
        Path current = directory.toAbsolutePath().normalize();
        Map<String, Object> values = new LinkedHashMap<>();
        if (current.getParent() != null) read(current.getParent().resolve(serviceFile), values);
        read(current.resolve(serviceFile), values);
        return values;
    }

    private static void read(Path file, Map<String, Object> values) {
        if (!Files.exists(file)) return;
        try {
            int number = 0;
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                number++;
                String line = raw.strip();
                if (number == 1 && line.startsWith("\uFEFF")) line = line.substring(1).strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).stripLeading();
                int separator = line.indexOf('=');
                if (separator < 1) throw invalid(file, number);
                String key = line.substring(0, separator).strip();
                if (!key.matches("[A-Za-z_][A-Za-z0-9_.-]*")) throw invalid(file, number);
                String value = line.substring(separator + 1).strip();
                if (value.startsWith("'") || value.startsWith("\"")) {
                    char quote = value.charAt(0);
                    int end = value.indexOf(quote, 1);
                    if (end < 0) throw invalid(file, number);
                    String remainder = value.substring(end + 1).strip();
                    if (!remainder.isEmpty() && !remainder.startsWith("#")) throw invalid(file, number);
                    value = value.substring(1, end);
                } else {
                    int comment = value.indexOf('#');
                    if (comment >= 0) value = value.substring(0, comment).stripTrailing();
                }
                values.put(key, value);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read local configuration: " + file, error);
        }
    }

    private static IllegalStateException invalid(Path file, int line) {
        // Never include a credential's value in startup errors.
        return new IllegalStateException("Invalid single-line dotenv setting: " + file + ":" + line);
    }
}
