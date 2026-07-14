package com.hengshucredit.rule.server.controller.sync;

import com.hengshucredit.rule.model.dto.ProjectTokenResponse;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ProjectAuthService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ProjectAuthTokenControllerTest {

    @Test
    public void issuesTokenFromTrustedBaseContext() {
        ProjectTokenResponse expected = new ProjectTokenResponse();
        expected.setTokenCode("TOKEN_A");
        FakeProjectAuthService service = new FakeProjectAuthService(cipher(), expected);
        ProjectAuthTokenController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext context = ProjectAuthContext.direct(7L, "credit", 11L,
                "BASIC_MAIN", ProjectAuthType.BASIC);
        context.attach(request);

        R<ProjectTokenResponse> response = controller.issueToken(request);

        assertEquals(200, response.getCode());
        assertSame(expected, response.getData());
        assertSame(context, service.issuedFrom);
    }

    @Test
    public void rejectsTemporaryTokenExchange() {
        FakeProjectAuthService service = new FakeProjectAuthService(cipher(), new ProjectTokenResponse());
        ProjectAuthTokenController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProjectAuthContext.temporary(7L, "credit", 11L, "BASIC_MAIN", ProjectAuthType.BASIC,
                21L, "TOKEN_A", "VALID").attach(request);

        assertEquals(401, controller.issueToken(request).getCode());
        assertNull(service.issuedFrom);
    }

    private ProjectAuthTokenController controller(ProjectAuthService service) {
        ProjectAuthTokenController controller = new ProjectAuthTokenController();
        ReflectionTestUtils.setField(controller, "projectAuthService", service);
        return controller;
    }

    private CredentialCipher cipher() {
        ProjectAuthProperties properties = new ProjectAuthProperties();
        properties.setMasterKeys(Collections.singletonMap("v1", "test-master-key"));
        return new CredentialCipher(properties);
    }

    private static class FakeProjectAuthService extends ProjectAuthService {
        private final ProjectTokenResponse response;
        private ProjectAuthContext issuedFrom;

        private FakeProjectAuthService(CredentialCipher cipher, ProjectTokenResponse response) {
            super(cipher);
            this.response = response;
        }

        @Override
        public ProjectTokenResponse issueToken(ProjectAuthContext context) {
            issuedFrom = context;
            return response;
        }
    }
}
