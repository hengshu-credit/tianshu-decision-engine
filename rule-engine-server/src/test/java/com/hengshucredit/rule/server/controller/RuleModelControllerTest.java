package com.hengshucredit.rule.server.controller;

import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.server.service.RuleModelService;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleModelControllerTest {

    @Test
    public void returnsOnnxRuntimeCapabilitiesForModelConfigurationPage() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("onnxRuntimeVersion", "1.26.0");
        capabilities.put("availableProviders", Arrays.asList("CPU", "CUDA"));
        capabilities.put("cudaAvailable", true);
        capabilities.put("modelRuntimeStates", Collections.singletonList(
                Collections.singletonMap("modelId", 7L)));
        RuleModelService service = new RuleModelService() {
            @Override
            public Map<String, Object> runtimeCapabilities() {
                return capabilities;
            }
        };
        RuleModelController controller = new RuleModelController();
        ReflectionTestUtils.setField(controller, "modelService", service);

        R<Map<String, Object>> response = controller.runtimeCapabilities();

        assertEquals(200, response.getCode());
        assertEquals("1.26.0", response.getData().get("onnxRuntimeVersion"));
        assertTrue((Boolean) response.getData().get("cudaAvailable"));
        assertEquals(7L, ((Map<?, ?>) ((Iterable<?>) response.getData()
                .get("modelRuntimeStates")).iterator().next()).get("modelId"));
    }

    @Test
    public void modelExecutionFailureIsNotReportedAsSuccessfulHttpResultOrLog() {
        RuleModelService service = new RuleModelService() {
            @Override public RuleModel getDetail(Long id) {
                RuleModel model = new RuleModel(); model.setId(id); model.setModelCode("risk"); model.setModelName("风险模型");
                return model;
            }

            @Override public Map<String, Object> execute(Long id, Map<String, Object> params) {
                return Map.of("success", false, "error", "模型输入不匹配");
            }
        };
        AtomicReference<com.hengshucredit.rule.model.entity.RuleRuntimeCallLog> saved = new AtomicReference<>();
        RuleRuntimeCallLogService logs = new RuleRuntimeCallLogService() {
            @Override public void safeSave(com.hengshucredit.rule.model.entity.RuleRuntimeCallLog log) { saved.set(log); }
        };
        RuleModelController controller = new RuleModelController();
        ReflectionTestUtils.setField(controller, "modelService", service);
        ReflectionTestUtils.setField(controller, "runtimeCallLogService", logs);

        R<Map<String, Object>> response = controller.execute(7L, "{}", null);

        assertEquals(500, response.getCode());
        assertEquals("模型输入不匹配", response.getMessage());
        assertEquals(Integer.valueOf(0), saved.get().getSuccess());
    }
}
