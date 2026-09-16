package com.hengshucredit.rule.server.service.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class DdlTableParserTest {
    @Test
    public void keepsAllColumnsAcrossLinesAndConstraints() {
        var objects = new DdlTableParser().parseCreateTables("CREATE TABLE Mixed_Table (User_ID bigint, PRIMARY KEY (User_ID), Amount decimal(12,2) COMMENT '额度,金额',\n Name varchar(30) COMMENT '名称');");
        assertEquals(1, objects.size());
        assertEquals("Mixed_Table", objects.get(0).getObjectCode());
        var fields = objects.get(0).getFields();
        assertEquals(3, fields.size());
        assertEquals("User_ID", fields.get(0).getFieldName());
        assertEquals("Amount", fields.get(1).getFieldName());
        assertEquals("额度,金额", fields.get(1).getFieldLabel());
        assertEquals("NUMBER", fields.get(1).getVarType());
        assertEquals("Name", fields.get(2).getFieldName());
    }
}
