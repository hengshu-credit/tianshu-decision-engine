package com.hengshucredit.rule.server.config;

import org.apache.coyote.http11.Http11Nio2Protocol;
import org.junit.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;

public class TomcatProtocolConfigTest {

    @Test
    public void usesNio2ProtocolWithoutJavaSelectorPipe() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();

        new TomcatProtocolConfig().customize(factory);

        assertEquals(Http11Nio2Protocol.class.getName(),
                ReflectionTestUtils.getField(factory, "protocol"));
    }
}
