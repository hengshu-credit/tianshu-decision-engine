package com.hengshucredit.rule.server.service.parser;

import com.hengshucredit.rule.model.dto.ParsedField;
import com.hengshucredit.rule.model.dto.ParsedObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JsonSchemaParserTest {

    @Test
    public void jsonConstantsPreserveListsObjectsAndEmptyStrings() {
        var constants = new JsonSchemaParser().parseConstants(
                "{\"Codes\":[\"A\",\"B\"],\"Limits\":{\"max\":5},\"Empty\":\"\"}").getConstants();
        assertEquals(3, constants.size());
        assertEquals("Codes", constants.get(0).getConstCode());
        assertEquals("LIST", constants.get(0).getConstType());
        assertEquals("[\"A\",\"B\"]", constants.get(0).getConstValue());
        assertEquals("MAP", constants.get(1).getConstType());
        assertEquals("{\"max\":5}", constants.get(1).getConstValue());
        assertEquals("", constants.get(2).getConstValue());
    }

    @Test
    public void nullConstantIsRejectedInsteadOfChangedToEmptyString() {
        assertThrows(IllegalArgumentException.class,
                () -> new JsonSchemaParser().parseConstants("{\"Missing\":null}"));
    }

    @Test
    public void arrayInferenceSkipsNullAndDoesNotInventAnEmptyListElementType() {
        var fields = new JsonSchemaParser().parseObject("{\"Apps\":[null,{\"Name\":\"A\"}],\"Empty\":[]}", "request").getFields();
        assertEquals("OBJECT", findByName(fields, "Apps").getGenericType());
        assertNotNull(findByName(fields, "Name"));
        assertNull(findByName(fields, "Empty").getGenericType());
        assertThrows(IllegalArgumentException.class,
                () -> new JsonSchemaParser().parseObject("{\"Mixed\":[1,\"A\"]}", "request"));
    }

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
