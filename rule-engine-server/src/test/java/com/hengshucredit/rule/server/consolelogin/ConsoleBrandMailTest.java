package com.hengshucredit.rule.server.consolelogin;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConsoleBrandMailTest {
    @Test public void remoteBrandLogoIsRenderedAsEscapedImageAddress() throws Exception {
        String html = ConsoleMailService.html("系统", "副标题", "系统通知", "管理员", "通知内容", null, null, 0,
                "https://images.example.com/logo.svg?v=1&mode=mail");
        assertTrue(html.contains("src=\"https://images.example.com/logo.svg?v=1&amp;mode=mail\""));
        assertFalse(html.contains("cid:tianshu-logo"));
        assertThrows(IllegalArgumentException.class, () -> ConsoleMailService.html("系统", "", "通知", "用户", "内容", null, null, 0, "javascript:alert(1)"));
    }
    @Test public void htmlContainsOneCompactSignatureAndEscapesValues() throws Exception {
        String html = ConsoleMailService.html("测试引擎", "副标题", "密码重置通知", "<script>bad</script>", "请设置密码", "https://engine.example.com/#/password-reset?token=example&value=1", "设置新密码", 30);
        assertTrue(html.contains("cid:tianshu-logo"));
        assertTrue(html.contains("上海衡枢真信科技有限公司"));
        assertEquals(html.indexOf("上海衡枢真信科技有限公司"), html.lastIndexOf("上海衡枢真信科技有限公司"));
        assertTrue(html.contains("www.hengshucredit.com"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("token=example&amp;value=1"));
        assertFalse(html.contains("notify@"));
        assertThrows(IllegalArgumentException.class, () -> ConsoleMailService.html("系统", "", "测试", "用户", "内容", "javascript:alert(1)", "操作", 30));
    }
}
