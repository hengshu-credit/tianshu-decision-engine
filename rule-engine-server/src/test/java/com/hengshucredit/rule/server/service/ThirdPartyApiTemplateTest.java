package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;
import com.hengshucredit.rule.model.entity.RuleExternalDatasource;
import com.hengshucredit.rule.server.mapper.RuleExternalDatasourceMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ThirdPartyApiTemplateTest {

    @Test
    public void allDisabledTemplatesCanGenerateRequestsWithoutCallingVendors() throws Exception {
        String sql = readResource("/sql/data-third-party-api.sql");
        List<Map<String, String>> datasourceRows = parseInsert(sql, "INSERT INTO rule_external_datasource");
        List<Map<String, String>> apiRows = parseInsert(sql, "INSERT INTO rule_external_api_config");
        Map<Long, RuleExternalDatasource> datasources = new LinkedHashMap<>();
        for (Map<String, String> row : datasourceRows) {
            RuleExternalDatasource datasource = new RuleExternalDatasource();
            datasource.setId(longValue(row.get("id")));
            datasource.setProtocol(textValue(row.get("protocol")));
            datasource.setBaseUrl(textValue(row.get("base_url")));
            datasource.setAuthType(textValue(row.get("auth_type")));
            datasource.setAuthConfig(textValue(row.get("auth_config")));
            datasource.setStatus(integerValue(row.get("status")));
            assertEquals(Integer.valueOf(0), datasource.getStatus());
            datasources.put(datasource.getId(), datasource);
        }

        ExternalApiInvokeService service = new ExternalApiInvokeService();
        ReflectionTestUtils.setField(service, "datasourceMapper", datasourceMapper(datasources));
        for (Map<String, String> row : apiRows) {
            RuleExternalApiConfig api = apiConfig(row);
            assertEquals(Integer.valueOf(0), api.getStatus());
            assertFalse(api.getRequestMapping() != null && api.getRequestMapping().contains("securityProfile"));
            Map<String, Object> sample = JSON.parseObject(api.getTestSampleParams(), LinkedHashMap.class);
            assertEquals(4, sample.size());
            replaceBaihangDemoKey(api);

            Map<String, Object> preview = service.previewRequest(api, sample, "PREVIEW_TOKEN_ONLY");

            assertEquals(false, preview.get("networkCalled"));
            assertFalse(String.valueOf(preview.get("url")).contains("${"));
        }

        assertEquals(15, datasourceRows.size());
        assertEquals(17, apiRows.size());
        assertFalse(sql.contains("securityProfile"));
    }

    private RuleExternalApiConfig apiConfig(Map<String, String> row) {
        RuleExternalApiConfig api = new RuleExternalApiConfig();
        api.setId(longValue(row.get("id")));
        api.setDatasourceId(longValue(row.get("datasource_id")));
        api.setApiCode(textValue(row.get("api_code")));
        api.setRequestMethod(textValue(row.get("request_method")));
        api.setEndpointUrl(textValue(row.get("endpoint_url")));
        api.setContentType(textValue(row.get("content_type")));
        api.setHeaderConfig(textValue(row.get("header_config")));
        api.setQueryConfig(textValue(row.get("query_config")));
        api.setRequestMapping(textValue(row.get("request_mapping")));
        api.setResponseMapping(textValue(row.get("response_mapping")));
        api.setBodyTemplate(textValue(row.get("body_template")));
        api.setRequestScript(textValue(row.get("request_script")));
        api.setResponseScript(textValue(row.get("response_script")));
        api.setAuthMode(textValue(row.get("auth_mode")));
        api.setAuthApiConfig(textValue(row.get("auth_api_config")));
        api.setTestSampleParams(textValue(row.get("test_sample_params")));
        api.setStatus(integerValue(row.get("status")));
        return api;
    }

    @SuppressWarnings("unchecked")
    private void replaceBaihangDemoKey(RuleExternalApiConfig api) {
        if (!api.getApiCode().startsWith("FRAI001")) return;
        Map<String, Object> config = JSON.parseObject(api.getAuthApiConfig(), LinkedHashMap.class);
        for (Object item : (List<?>) config.get("scriptVariables")) {
            Map<String, Object> variable = (Map<String, Object>) item;
            if ("secretKey".equals(variable.get("name"))) {
                variable.put("value", "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1u");
            }
        }
        api.setAuthApiConfig(JSON.toJSONString(config));
    }

    @SuppressWarnings("unchecked")
    private RuleExternalDatasourceMapper datasourceMapper(Map<Long, RuleExternalDatasource> datasources) {
        return (RuleExternalDatasourceMapper) Proxy.newProxyInstance(
                RuleExternalDatasourceMapper.class.getClassLoader(),
                new Class<?>[]{RuleExternalDatasourceMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return datasources.get(Long.valueOf(String.valueOf(args[0])));
                    }
                    if ("toString".equals(method.getName())) return "ThirdPartyDatasourceMapperProxy";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    return null;
                });
    }

    private List<Map<String, String>> parseInsert(String sql, String marker) {
        int start = sql.indexOf(marker);
        int next = sql.indexOf("INSERT INTO ", start + marker.length());
        String block = sql.substring(start, next < 0 ? sql.length() : next);
        int columnStart = block.indexOf('(');
        int valuesStart = block.indexOf(")\nVALUES", columnStart);
        List<String> columns = splitTopLevel(block.substring(columnStart + 1, valuesStart), ',');
        String values = block.substring(valuesStart + ")\nVALUES".length());
        List<String> tuples = tupleValues(values);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String tuple : tuples) {
            List<String> fields = splitTopLevel(tuple, ',');
            assertEquals(columns.size(), fields.size());
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                row.put(columns.get(i).trim(), fields.get(i).trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private List<String> tupleValues(String values) {
        List<String> result = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < values.length(); i++) {
            char value = values.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '\'') quoted = false;
                continue;
            }
            if (value == '\'') quoted = true;
            else if (value == '(') {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (value == ')') {
                depth--;
                if (depth == 0 && start >= 0) result.add(values.substring(start, i));
            }
        }
        return result;
    }

    private List<String> splitTopLevel(String source, char delimiter) {
        List<String> result = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            char value = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (value == '\\') escaped = true;
                else if (value == '\'') quoted = false;
                continue;
            }
            if (value == '\'') quoted = true;
            else if (value == '(') depth++;
            else if (value == ')') depth--;
            else if (value == delimiter && depth == 0) {
                result.add(source.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(source.substring(start).trim());
        return result;
    }

    private String textValue(String value) {
        if (value == null || "NULL".equalsIgnoreCase(value)) return null;
        if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
            return value.substring(1, value.length() - 1)
                    .replace("\\\\", "\\")
                    .replace("\\'", "'");
        }
        return value;
    }

    private Long longValue(String value) {
        return Long.valueOf(value.trim());
    }

    private Integer integerValue(String value) {
        return Integer.valueOf(value.trim());
    }

    private String readResource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IllegalArgumentException("resource not found: " + path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) >= 0) output.write(buffer, 0, length);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
