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

        R<ProjectAuthDTO> response = controller.create(7L, request);

        assertEquals(200, response.getCode());
        assertSame(expected, response.getData());
        assertSame(request, service.request);
        assertEquals(Long.valueOf(7L), service.projectId);
    }

    @Test
    public void returnsBadRequestForCrossProjectAccess() {
        service.error = new IllegalArgumentException("Authentication configuration not found for project");

        R<ProjectAuthDTO> response = controller.getFull(8L, 11L);

        assertEquals(400, response.getCode());
        assertEquals("Authentication configuration not found for project", response.getMessage());
    }

    @Test
    public void delegatesTokenAndAccessLogQueries() {
        Page<ProjectAuthTokenDTO> tokens = new Page<>(2, 20);
        Page<RuleAuthAccessLog> logs = new Page<>(3, 30);
        service.tokens = tokens;
        service.logs = logs;

        R<IPage<ProjectAuthTokenDTO>> tokenResponse = controller.listTokens(7L, 11L, 2, 20);
        R<IPage<RuleAuthAccessLog>> logResponse = controller.listAccessLogs(
                7L, 3, 30, "BASIC_MAIN", "TOKEN_A", 1);

        assertSame(tokens, tokenResponse.getData());
        assertSame(logs, logResponse.getData());
        assertEquals(Long.valueOf(11L), service.authId);
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
        private String tokenCode;
        private Integer success;
        private IllegalArgumentException error;

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
        public IPage<ProjectAuthTokenDTO> pageTokens(Long projectId, Long authId, int pageNum, int pageSize) {
            this.projectId = projectId;
            this.authId = authId;
            return tokens;
        }

        @Override
        public IPage<RuleAuthAccessLog> pageAccessLogs(Long projectId, int pageNum, int pageSize,
                                                       String authCode, String tokenCode, Integer success) {
            this.projectId = projectId;
            this.authCode = authCode;
            this.tokenCode = tokenCode;
            this.success = success;
            return logs;
        }
    }
}
