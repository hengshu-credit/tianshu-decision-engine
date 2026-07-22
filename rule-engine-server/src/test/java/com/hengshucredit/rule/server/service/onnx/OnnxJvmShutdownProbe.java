package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;

import java.util.Arrays;

public final class OnnxJvmShutdownProbe {

    private OnnxJvmShutdownProbe() {
    }

    public static void main(String[] args) throws Exception {
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        for (String name : Arrays.asList(
                "det_10g.onnx",
                "w600k_r50.onnx",
                "2d106det.onnx",
                "1k3d68.onnx",
                "genderage.onnx")) {
            manager.session(OnnxTestAssets.read("onnx/buffalo_l/" + name), cuda);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(manager::close, "SpringContextShutdownHook"));
    }
}
