package com.hengshucredit.rule.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Only compares identifiers. The caller retains the original spelling. */
public final class ReservedContentCode {
    private record Catalog(Set<String> keywords, Set<String> engineCodes, Set<String> managedFunctions,
                           Map<String, String> resourceFields, Set<String> engineCodePatterns) { }
    private static final Catalog CATALOG = load();

    private static final Pattern GENERATED = Pattern.compile(String.join("|", CATALOG.engineCodePatterns()));

    private ReservedContentCode() { }

    private static Catalog load() {
        try (InputStream input = ReservedContentCode.class.getResourceAsStream("/reserved-codes.json")) {
            Catalog source = new ObjectMapper().readValue(input, Catalog.class);
            return new Catalog(fold(source.keywords()), fold(source.engineCodes()), fold(source.managedFunctions()), Map.copyOf(source.resourceFields()), source.engineCodePatterns());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Set<String> fold(Set<String> codes) {
        return codes.stream().map(code -> code.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static String resourceField(String type) {
        return CATALOG.resourceFields().get(type);
    }

    public static String error(String value) {
        if (value == null) return null;
        String code = value.strip();
        String key = code.toLowerCase(Locale.ROOT);
        if (CATALOG.keywords().contains(key)) return "“" + code + "”是语言关键字，请更换编码";
        if (CATALOG.engineCodes().contains(key) || CATALOG.managedFunctions().contains(key) || GENERATED.matcher(key).matches()) return "“" + code + "”是引擎内置编码，请更换编码";
        return null;
    }

    /** A previously immutable code may be corrected once if the stored value now collides. */
    public static boolean sameOrRepaired(String stored, String proposed) {
        return java.util.Objects.equals(stored, proposed)
                || error(stored) != null && proposed != null && !proposed.isBlank() && error(proposed) == null;
    }

    public static void requireAllowed(String value) {
        String error = error(value);
        if (error != null) throw new IllegalArgumentException(error);
    }
}
