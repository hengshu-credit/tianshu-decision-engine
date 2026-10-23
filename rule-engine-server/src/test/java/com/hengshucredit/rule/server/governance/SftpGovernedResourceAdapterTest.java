package com.hengshucredit.rule.server.governance;

import org.junit.Test;
import static org.junit.Assert.*;

public class SftpGovernedResourceAdapterTest {
    private final SftpGovernedResourceAdapter adapter = new SftpGovernedResourceAdapter(null, null, null);

    @Test
    public void projectScopedSourceMustHaveRealProjectId() {
        assertTrue(adapter.validate(snapshot("\"scope\":\"PROJECT\",\"projectId\":0")).stream()
                .anyMatch(issue -> "$.projectId".equals(issue.referencePath())));
    }

    @Test
    public void invalidPoolAndPortAreReportedWithoutSilentlyClamping() {
        var issues = adapter.validate(snapshot("\"scope\":\"GLOBAL\",\"minIdle\":9,\"maxPoolSize\":8,\"port\":70000"));
        assertTrue(issues.stream().anyMatch(issue -> "$.minIdle".equals(issue.referencePath())));
        assertTrue(issues.stream().anyMatch(issue -> "$.port".equals(issue.referencePath())));
    }

    @Test
    public void zeroIdleAndAbsentOptionalDefaultsAreValid() {
        assertTrue(adapter.validate(snapshot("\"scope\":\"GLOBAL\",\"minIdle\":0")).isEmpty());
        assertTrue(adapter.validate(snapshot("\"scope\":\"PROJECT\",\"projectId\":5")).isEmpty());
    }

    private ResourceSnapshot snapshot(String fields) {
        return ResourceSnapshot.ofJson("{\"datasourceCode\":\"Keep_Code\",\"datasourceName\":\"文件源\",\"host\":\"test.invalid\",\"username\":\"user\"," + fields + "}");
    }
}
