package com.hengshucredit.rule.server.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class GlobalExceptionHandlerTest {

    @Test
    public void staticResourceMissReturnsPlain404InsteadOfUnhandled500() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MissingResourceController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String content = mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = new ObjectMapper().readTree(content);
        org.junit.Assert.assertEquals("501001", body.get("code").asText());
        org.junit.Assert.assertEquals("资源不存在", body.get("message").asText());
    }

    @RestController
    private static final class MissingResourceController {
        @GetMapping("/missing")
        public void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/favicon.ico");
        }
    }
}
