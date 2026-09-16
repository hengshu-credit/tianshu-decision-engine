package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.RuleListRecordChangeRequest;
import com.hengshucredit.rule.model.entity.RuleListRecordLog;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.server.service.RuleListService;
import com.hengshucredit.rule.server.security.RequirePermission;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RuleListControllerTest {

    @Test
    public void logFiltersBindAndReachThePaginatedService() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 31, 23, 59, 59);
        int[] calls = {0};
        RuleListService service = new RuleListService() {
            @Override
            public IPage<RuleListRecordLog> pageLogs(Long listId, int pageNum, int pageSize, Long recordId,
                    String itemType, String itemContent, String operation, String keyword,
                    LocalDateTime startTime, LocalDateTime endTime) {
                calls[0]++;
                Assert.assertEquals(Long.valueOf(9), listId);
                Assert.assertEquals(2, pageNum);
                Assert.assertEquals(30, pageSize);
                Assert.assertNull(recordId);
                Assert.assertNull(itemContent);
                Assert.assertEquals("MOBILE", itemType);
                Assert.assertEquals("UPDATE", operation);
                Assert.assertEquals("核验", keyword);
                Assert.assertEquals(start, startTime);
                Assert.assertEquals(end, endTime);
                return new Page<>(pageNum, pageSize);
            }
        };
        RuleListController controller = new RuleListController();
        ReflectionTestUtils.setField(controller, "listService", service);

        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/rule/list/9/log")
                .param("pageNum", "2").param("pageSize", "30")
                .param("itemType", "MOBILE").param("operation", "UPDATE").param("keyword", "核验")
                .param("startTime", "2026-08-01 00:00:00").param("endTime", "2026-08-31 23:59:59"))
                .andExpect(status().isOk());

        Assert.assertEquals(1, calls[0]);
    }

    @Test
    public void recordWritesExposeOnlyApprovalBatchEndpoints()
            throws Exception {
        Method single = RuleListController.class.getMethod(
                "stageRecordChange", Long.class,
                RuleListRecordChangeRequest.class);
        Method imported = RuleListController.class.getMethod(
                "importRecords", Long.class, MultipartFile.class);

        Assert.assertArrayEquals(
                new String[]{"/{listId:\\d+}/change-batch"},
                single.getAnnotation(PostMapping.class).value());
        Assert.assertEquals("approval:submit",
                single.getAnnotation(RequirePermission.class).value());
        Assert.assertEquals("approval:submit",
                imported.getAnnotation(RequirePermission.class).value());
        for (String removed : new String[]{
                "createRecord", "updateRecord", "deleteRecord"}) {
            Assert.assertFalse(Arrays.stream(
                            RuleListController.class.getDeclaredMethods())
                    .anyMatch(method -> removed.equals(method.getName())));
        }
    }
}
