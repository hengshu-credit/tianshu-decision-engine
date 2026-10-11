package com.hengshucredit.rule.server.controller;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.service.RuleModelService;
import com.hengshucredit.rule.server.mapper.RuleModelVersionMapper;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class ModelVersionTestControllerTest {
    @Test
    public void explicitlyRequestedMissingVersionMustNotExecuteLatestModel() throws Exception {
        AtomicInteger latestExecutions = new AtomicInteger();
        RuleModelService service = new RuleModelService() {
            @Override public RuleModel getDetail(Long id) {
                RuleModel model = new RuleModel();
                model.setId(id);
                model.setModelCode("VersionedModel");
                model.setModelFormat("PMML");
                model.setCurrentVersion(2);
                return model;
            }
            @Override public Map<String, Object> execute(Long id, Map<String, Object> params) {
                latestExecutions.incrementAndGet();
                return Map.of("success", true, "outputs", Map.of("score", 99));
            }
        };
        ReflectionTestUtils.setField(service, "versionMapper", Proxy.newProxyInstance(
                RuleModelVersionMapper.class.getClassLoader(), new Class<?>[]{RuleModelVersionMapper.class},
                (proxy, method, args) -> null));
        RuleModelController controller = new RuleModelController();
        ReflectionTestUtils.setField(controller, "modelService", service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        var response = mvc.perform(post("/api/rule/model/execute/7")
                .param("version", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"Age\":25}")).andReturn().getResponse();

        assertEquals(200, response.getStatus());
        var body = JSON.parseObject(response.getContentAsString());
        assertNotEquals("指定不存在版本不能得到最新模型的成功响应", Integer.valueOf(200), body.getInteger("code"));
        assertEquals("带版本请求必须进入版本执行路径", 0, latestExecutions.get());
        assertTrue(body.getString("message"), body.getString("message").contains("版本"));
    }
}
