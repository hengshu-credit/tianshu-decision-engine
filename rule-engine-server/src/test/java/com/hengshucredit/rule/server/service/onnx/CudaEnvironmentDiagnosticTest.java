package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CudaEnvironmentDiagnosticTest {

    @Test
    public void runsTianshuFaceDetectionAndRecognitionOnCuda() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("tianshu.cuda.diagnostic"));
        System.out.println("PATH=" + System.getenv("PATH"));
        System.out.println("java.library.path=" + System.getProperty("java.library.path"));
        System.loadLibrary("cublasLt64_12");
        System.loadLibrary("cublas64_12");
        System.loadLibrary("cufft64_11");
        System.loadLibrary("cudart64_12");
        String cudnnBinProperty = System.getProperty("tianshu.cudnn.bin");
        assertNotNull("tianshu.cudnn.bin is required when CUDA diagnostics are enabled", cudnnBinProperty);
        Path cudnnBin = Paths.get(cudnnBinProperty);
        for (String library : new String[]{
                "cudnn_graph64_9.dll",
                "cudnn_ops64_9.dll",
                "cudnn_adv64_9.dll",
                "cudnn_cnn64_9.dll",
                "cudnn_heuristic64_9.dll",
                "cudnn_engines_precompiled64_9.dll",
                "cudnn_engines_runtime_compiled64_9.dll",
                "cudnn64_9.dll"
        }) {
            System.load(cudnnBin.resolve(library).toString());
        }
        OnnxRuntimeConfig cuda = OnnxRuntimeConfig.from(JSON.parseObject(
                "{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":0}"));
        OnnxRuntimeSessionManager manager = new OnnxRuntimeSessionManager();
        try {
            String image = OnnxTestAssets.imageBase64();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> faces = (List<Map<String, Object>>) (List<?>)
                    new ScrfdFaceDetectionExecutor(manager, cuda).execute(
                            OnnxTestAssets.read("onnx/buffalo_l/det_10g.onnx"), image,
                            OnnxTaskConfig.parse("{\"onnxTaskType\":\"SCRFD_FACE_DETECTION\"}"))
                            .get("faces");
            Map<String, Object> recognition = new ArcFaceRecognitionExecutor(manager, cuda).execute(
                    OnnxTestAssets.read("onnx/buffalo_l/w600k_r50.onnx"), image, faces);
            Map<?, ?> first = (Map<?, ?>) ((List<?>) recognition.get("results")).get(0);
            List<?> embedding = (List<?>) first.get("normalizedEmbedding");

            assertEquals(1, faces.size());
            assertEquals(512, embedding.size());
            assertEquals(1d, norm(embedding), 0.0001d);
            assertEquals(0, manager.getActiveCpuFallbackCount());
            assertTrue(manager.getCachedSessionCount() >= 2);
            System.out.println("CUDA_FACE_RESULT faces=" + faces.size()
                    + ", embeddingDimensions=" + embedding.size()
                    + ", normalizedNorm=" + norm(embedding)
                    + ", cpuFallbacks=" + manager.getActiveCpuFallbackCount());
        } finally {
            manager.close();
        }
    }

    private static double norm(List<?> values) {
        double sum = 0d;
        for (Object value : values) {
            double number = ((Number) value).doubleValue();
            sum += number * number;
        }
        return Math.sqrt(sum);
    }
}
