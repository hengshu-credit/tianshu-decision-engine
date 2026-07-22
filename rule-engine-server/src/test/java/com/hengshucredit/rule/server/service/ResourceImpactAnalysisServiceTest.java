package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.ResourceImpactAnalysis;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResourceImpactAnalysisServiceTest {

    @Test
    public void currentImpactTokenCanBeConfirmedOnce() {
        FixtureService service = new FixtureService();
        service.references.add("REVIEW:revision-2");
        ResourceImpactAnalysis analysis = service.analyze("MODEL", 7L, "DELETE", "alice");

        ResourceImpactAnalysis confirmed = service.assertCurrent(
                analysis.getAnalysisToken(), "MODEL", 7L, "DELETE");

        Assert.assertEquals("CONFIRMED", confirmed.getStatus());
        Assert.assertNotNull(confirmed.getConfirmTime());
        Assert.assertThrows(IllegalStateException.class, () -> service.assertCurrent(
                analysis.getAnalysisToken(), "MODEL", 7L, "DELETE"));
    }

    @Test
    public void expiryAndReferenceChangesInvalidateToken() {
        FixtureService service = new FixtureService();
        ResourceImpactAnalysis expired = service.analyze("MODEL", 7L, "OFFLINE", "alice");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        Assert.assertThrows(IllegalStateException.class, () -> service.assertCurrent(
                expired.getAnalysisToken(), "MODEL", 7L, "OFFLINE"));

        ResourceImpactAnalysis stale = service.analyze("MODEL", 7L, "DELETE", "alice");
        service.references.add("PUBLISHED:artifact-9");
        Assert.assertThrows(IllegalStateException.class, () -> service.assertCurrent(
                stale.getAnalysisToken(), "MODEL", 7L, "DELETE"));
    }

    private static final class FixtureService extends ResourceImpactAnalysisService {
        private final Map<String, ResourceImpactAnalysis> analyses = new LinkedHashMap<>();
        private final List<String> references = new ArrayList<>();
        private long id = 1L;

        @Override
        protected Map<String, Object> currentImpactReport(String resourceType, Long resourceId,
                                                          String action) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("resourceType", resourceType);
            report.put("resourceId", resourceId);
            report.put("action", action);
            report.put("references", new ArrayList<>(references));
            return report;
        }

        @Override
        protected void insertAnalysis(ResourceImpactAnalysis analysis) {
            analysis.setId(id++);
            analyses.put(analysis.getAnalysisToken(), analysis);
        }

        @Override
        protected ResourceImpactAnalysis loadAnalysis(String token) {
            return analyses.get(token);
        }

        @Override
        protected void updateAnalysis(ResourceImpactAnalysis analysis) {
        }

        @Override
        protected String confirmingActor() {
            return "bob";
        }
    }
}
