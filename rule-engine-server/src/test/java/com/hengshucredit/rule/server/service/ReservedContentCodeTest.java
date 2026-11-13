package com.hengshucredit.rule.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.core.function.AggregateBuiltinFunctionRegistry;
import org.junit.Test;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ReservedContentCodeTest {
    @Test
    public void previouslyImmutableCodesCanOnlyChangeToResolveAStoredCollision() {
        assertTrue(ReservedContentCode.sameOrRepaired("select", "customerSelection"));
        assertFalse(ReservedContentCode.sameOrRepaired("select", "sum"));
        assertFalse(ReservedContentCode.sameOrRepaired("customerSelection", "otherName"));
    }

    @Test
    public void shippedCatalogAgreesWithRegisteredFunctionsAndSeededConstants() throws Exception {
        var mapper = new ObjectMapper();
        var catalog = mapper.readTree(getClass().getResource("/reserved-codes.json"));
        var managed = BuiltinFunctionCatalog.definitions().stream().map(row -> row.getFuncCode()).collect(Collectors.toSet());
        List<String> advertised = mapper.readerForListOf(String.class).readValue(catalog.get("managedFunctions"));
        assertEquals(managed, Set.copyOf(advertised));
        for (String name : managed) assertNotNull(name, ReservedContentCode.error(name));
        for (var row : BuiltinFieldValidationCatalog.definitions()) assertNotNull(ReservedContentCode.error(row.getValidationCode()));
        for (String name : AggregateBuiltinFunctionRegistry.managementCodes()) assertNotNull(name, ReservedContentCode.error(name));

        try (var input = getClass().getResourceAsStream("/sql/schema.sql")) {
            var schema = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var constants = java.util.regex.Pattern.compile("SELECT 1, 0, 'GLOBAL', '([^']+)'").matcher(schema);
            assertTrue(constants.find());
            do { assertNotNull(constants.group(1), ReservedContentCode.error(constants.group(1))); } while (constants.find());
        }
    }
}
