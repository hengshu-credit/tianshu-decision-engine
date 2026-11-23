package com.hengshucredit.rule.server.system;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.Map;
import java.util.stream.Collectors;

/** 邮件使用与控制台一致的字体、可变轴和中文后备字体。 */
public record EngineMailFont(String family, String variation, Resource file, String mimeType) {
    private static final String DEFAULT_FAMILY = "阿里妈妈方圆体 VF";

    public static EngineMailFont engineDefault() {
        return new EngineMailFont(DEFAULT_FAMILY, "'wght' 600, 'BEVL' 80",
                new ClassPathResource("brand/fonts/font.woff2"), "font/woff2");
    }

    static String axes(Map<?, ?> coordinates) {
        if (coordinates.isEmpty()) return "normal";
        return coordinates.entrySet().stream()
                .map(axis -> quote(axis.getKey().toString()) + " " + axis.getValue())
                .collect(Collectors.joining(", "));
    }

    public String faceCss() {
        return "/* 阿里妈妈方圆体 VF 版权所有 © 淘宝（中国）软件有限公司（阿里妈妈） */"
                + "@font-face{font-family:'TianshuMailFace';src:url('cid:tianshu-font');font-style:normal;font-weight:100 900}";
    }

    public String inlineCss() {
        String stack = mimeType.equals("font/woff2") && !DEFAULT_FAMILY.equals(family)
                ? quote(family) + ",'TianshuMailFace'" : "'TianshuMailFace'," + quote(family);
        return "font-family:" + stack + ",'PingFang SC','Microsoft YaHei',sans-serif;font-variation-settings:" + variation;
    }

    private static String quote(String value) {
        var css = new StringBuilder("'");
        value.codePoints().forEach(c -> {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') css.appendCodePoint(c);
            else css.append('\\').append(Integer.toHexString(c)).append(' ');
        });
        return css.append('\'').toString();
    }
}
