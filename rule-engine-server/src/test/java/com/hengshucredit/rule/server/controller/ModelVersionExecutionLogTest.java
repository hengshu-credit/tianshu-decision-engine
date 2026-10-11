package com.hengshucredit.rule.server.controller;

import com.alibaba.fastjson.JSON;
import com.hengshucredit.rule.model.entity.RuleModel;
import com.hengshucredit.rule.model.entity.RuleRuntimeCallLog;
import com.hengshucredit.rule.server.service.RuleModelService;
import com.hengshucredit.rule.server.service.RuleRuntimeCallLogService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ModelVersionExecutionLogTest {
    @Test
    public void executionLogLabelsAndRequestIdentityComeFromSelectedVersion() {
        RuleModelController controller = new RuleModelController();
        ReflectionTestUtils.setField(controller, "modelService", new RuleModelService() {
            @Override public RuleModel getDetail(Long id) {
                RuleModel current = new RuleModel(); current.setId(id); current.setModelCode("RenamedModel"); current.setModelName("当前名称");
                return current;
            }
            @Override public Map<String, Object> executeVersion(Long id, Integer version, Map<String, Object> params) {
                return Map.of("success", true, "version", version, "modelCode", "OriginalModel", "modelName", "原始名称",
                        "projectId", "3", "projectCode", "OriginalProject", "modelDigest", "file-one", "configurationDigest", "snapshot-one");
            }
        });
        AtomicReference<RuleRuntimeCallLog> saved = new AtomicReference<>();
        ReflectionTestUtils.setField(controller, "runtimeCallLogService", new RuleRuntimeCallLogService() {
            @Override public void safeSave(RuleRuntimeCallLog log) { saved.set(log); }
        });
        assertEquals(200, controller.execute(7L, "{\"Age\":25}", 1).getCode());
        assertEquals("OriginalModel", saved.get().getTargetCode());
        assertEquals("原始名称", saved.get().getTargetName());
        assertEquals(Long.valueOf(3L), saved.get().getProjectId());
        var request = JSON.parseObject(saved.get().getRequestBody());
        assertEquals(Integer.valueOf(1), request.getInteger("version"));
        assertEquals(Integer.valueOf(25), request.getJSONObject("params").getInteger("Age"));
    }
}
