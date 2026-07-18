package com.hengshucredit.rule.server.service.onnx;

import org.springframework.util.StreamUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

final class OnnxTestAssets {

    private static final String ROOT = "/assets/";

    static byte[] read(String relativePath) throws IOException {
        try (InputStream input = OnnxTestAssets.class.getResourceAsStream(ROOT + relativePath)) {
            if (input == null) {
                throw new FileNotFoundException("Missing test asset: " + ROOT + relativePath);
            }
            return StreamUtils.copyToByteArray(input);
        }
    }

    static String imageBase64() throws IOException {
        return Base64.getEncoder().encodeToString(read("docs/face.jpg"));
    }

    private OnnxTestAssets() {
    }
}
