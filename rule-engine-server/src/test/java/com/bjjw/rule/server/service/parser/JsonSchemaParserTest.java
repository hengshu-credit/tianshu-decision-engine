package com.bjjw.rule.server.service.parser;

import com.bjjw.rule.model.dto.ParsedField;
import com.bjjw.rule.model.dto.ParsedObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JsonSchemaParserTest {

    @Test
    public void parseNestedJsonKeepsOneObjectAndParentFieldTree() {
        JsonSchemaParser parser = new JsonSchemaParser();
        ParsedObject parsed = parser.parseObject("{\"definitionId\":1,\"params\":{\"taxpayerType\":\"一般纳税人\",\"goodsCategory\":\"货物\"}}", "request");

        assertEquals("request", parsed.getObjectCode());
        assertTrue(parsed.getNestedObjects().isEmpty());

        List<ParsedField> fields = parsed.getFields();
        ParsedField params = findByName(fields, "params");
        ParsedField taxpayerType = findByName(fields, "taxpayerType");
        ParsedField goodsCategory = findByName(fields, "goodsCategory");

        assertEquals("OBJECT", params.getVarType());
        assertNotNull(params.getTempId());
        assertNull(params.getParentFieldId());
        assertEquals("params", params.getScriptName());

        assertEquals(params.getTempId(), taxpayerType.getParentFieldId());
        assertEquals("params.taxpayerType", taxpayerType.getScriptName());
        assertEquals(params.getTempId(), goodsCategory.getParentFieldId());
        assertEquals("params.goodsCategory", goodsCategory.getScriptName());
    }

    private ParsedField findByName(List<ParsedField> fields, String name) {
        return fields.stream()
                .filter(field -> name.equals(field.getFieldName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing field: " + name));
    }
}
