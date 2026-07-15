package com.hengshucredit.rule.server.controller.mgmt;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.model.dto.ProjectAuthTokenDTO;
import com.hengshucredit.rule.model.entity.RuleAuthAccessLog;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ProjectAuthControllerTest {

    private FakeProjectAuthService service;
    private ProjectAuthController controller;

    @Before
    public void setUp() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        service = new FakeProjectAuthService(new CredentialCipher(properties));
        controller = new ProjectAuthController();
        ReflectionTestUtils.setField(controller, "projectAuthService", service);
    }

    @Test
    public void listsMaskedAuthenticationConfigurations() {
        ProjectAuthDTO dto = new ProjectAuthDTO();
        dto.setId(11L);
        dto.setSecretMasked("abcd****wxyz");
        service.auths = Collections.singletonList(dto);

        R<List<ProjectAuthDTO>> response = controller.list(7L);

        assertEquals(200, response.getCode());
        assertEquals(Long.valueOf(7L), service.projectId);
        assertNull(response.getData().get(0).getSecret());
    }

    @Test
    public void createsAuthenticationForPathProject() {
        ProjectAuthSaveRequest request = new ProjectAuthSaveRequest();
        ProjectAuthDTO expected = new ProjectAuthDTO();
        expected.setId(11L);
        service.auth = expected;

        R<ProjectAuthDTO> response = controller.create(7L, request, new MockHttpServletRequest());

        assertEquals(200, response.getCode());
        assertSame(expected, response.getData());
        assertSame(request, service.request);
        assertEquals(Long.valueOf(7L), service.projectId);
    }

    @Test
    public void returnsBadRequestForCrossProjectAccess() {
        service.error = new IllegalArgumentException("Authentication configuration not found for project");

        R<ProjectAuthDTO> response = controller.getFull(
                8L, 11L, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertEquals(400, response.getCode());
        assertEquals("Authentication configuration not found for project", response.getMessage());
    }

    @Test
    public void fullCredentialResponsesDisableCachingAndWriteAudit() {
        ProjectAuthDTO full = new ProjectAuthDTO();
        full.setId(11L);
        service.auth = full;
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/rule/project/7/auth/11/full");
        MockHttpServletResponse response = new MockHttpServletResponse();

        R<ProjectAuthDTO> result = controller.getFull(7L, 11L, request, response);

        assertSame(full, result.getData());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("no-cache", response.getHeader("Pragma"));
        assertSame(request, service.auditRequest);
        assertEquals(Long.valueOf(11L), service.auditAuthId);
    }

    @Test
    public void delegatesTokenAndAccessLogQueries() {
        Page<ProjectAuthTokenDTO> tokens = new Page<>(2, 20);
        Page<RuleAuthAccessLog> logs = new Page<>(3, 30);
        service.tokens = tokens;
        service.logs = logs;

        R<IPage<ProjectAuthTokenDTO>> tokenResponse = controller.listTokens(7L, 11L, 2, 20);
        R<IPage<RuleAuthAccessLog>> logResponse = controller.listAccessLogs(
                7L, 3, 30, "BASIC", "BASIC_MAIN", "TOKEN_A", 1,
                "2026-07-01", "2026-07-15");

        assertSame(tokens, tokenResponse.getData());
        assertSame(logs, logResponse.getData());
        assertEquals(Long.valueOf(11L), service.authId);
        assertEquals("BASIC", service.authType);
        assertEquals("BASIC_MAIN", service.authCode);
        assertEquals("TOKEN_A", service.tokenCode);
        assertEquals(Integer.valueOf(1), service.success);
    }

    private static class FakeProjectAuthService extends ProjectAuthService {
        private Long projectId;
        private Long authId;
        private ProjectAuthSaveRequest request;
        private List<ProjectAuthDTO> auths = Collections.emptyList();
        private ProjectAuthDTO auth;
        private IPage<ProjectAuthTokenDTO> tokens;
        private IPage<RuleAuthAccessLog> logs;
        private String authCode;
        private String authType;
        private String tokenCode;
        private Integer success;
        private IllegalArgumentException error;
        private MockHttpServletRequest auditRequest;
        private Long auditAuthId;

        private FakeProjectAuthService(CredentialCipher cipher) {
            super(cipher);
        }

        @Override
        public List<ProjectAuthDTO> listAuths(Long projectId) {
            this.projectId = projectId;
            return auths;
        }

        @Override
        public ProjectAuthDTO createAuth(Long projectId, ProjectAuthSaveRequest request) {
            this.projectId = projectId;
            this.request = request;
            return auth;
        }

        @Override
        public ProjectAuthDTO getFullAuth(Long projectId, Long authId) {
            if (error != null) throw error;
            this.projectId = projectId;
            this.authId = authId;
            return auth;
        }

        @Override
        public void recordManagementAccess(javax.servlet.http.HttpServletRequest request,
                                           Long projectId, Long authId, Long tokenId) {
            this.auditRequest = (MockHttpServletRequest) request;
            this.auditAuthId = authId;
        }

        @Override
        public IPage<ProjectAuthTokenDTO> pageTokens(Long projectId, Long authId, int pageNum, int pageSize) {
            this.projectId = projectId;
            this.authId = authId;
            return tokens;
        }

        @Override
        public IPage<RuleAuthAccessLog> pageAccessLogs(Long projectId, int pageNum, int pageSize,
                                                       String authType, String authCode, String tokenCode,
                                                       Integer success, String beginTime, String endTime) {
            this.projectId = projectId;
            this.authType = authType;
            this.authCode = authCode;
            this.tokenCode = tokenCode;
            this.success = success;
            return logs;
        }
    }
}
