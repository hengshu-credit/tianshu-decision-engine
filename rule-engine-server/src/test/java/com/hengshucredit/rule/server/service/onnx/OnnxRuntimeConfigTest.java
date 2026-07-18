package com.hengshucredit.rule.server.service.onnx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class OnnxRuntimeConfigTest {

    @Test
    public void defaultsToCpuAndWritesCanonicalValues() {
        OnnxRuntimeConfig config = OnnxRuntimeConfig.from(new JSONObject());
        JSONObject target = new JSONObject();

        config.applyTo(target);

        assertEquals("CPU", config.getExecutionProvider());
        assertFalse(config.isCuda());
        assertEquals("CPU", config.cacheKey());
        assertEquals("CPU", target.getString("executionProvider"));
        assertEquals(0, target.getIntValue("cudaDeviceId"));
        assertEquals(0L, target.getLongValue("cudaGpuMemLimitMb"));
        assertEquals("kNextPowerOfTwo", target.getString("cudaArenaExtendStrategy"));
        assertEquals("EXHAUSTIVE", target.getString("cudaCudnnConvAlgoSearch"));
        assertTrue(target.getBooleanValue("cudaDoCopyInDefaultStream"));
    }

    @Test
    public void normalizesCudaConfigurationAndSeparatesCacheKeys() {
        JSONObject firstValues = JSON.parseObject("{\"executionProvider\":\"cuda\",\"cudaDeviceId\":0,"
                + "\"cudaGpuMemLimitMb\":4096,\"cudaArenaExtendStrategy\":\"kSameAsRequested\","
                + "\"cudaCudnnConvAlgoSearch\":\"heuristic\",\"cudaDoCopyInDefaultStream\":false}");
        JSONObject secondValues = JSON.parseObject(firstValues.toJSONString());
        secondValues.put("cudaDeviceId", 1);

        OnnxRuntimeConfig first = OnnxRuntimeConfig.from(firstValues);
        OnnxRuntimeConfig second = OnnxRuntimeConfig.from(secondValues);

        assertTrue(first.isCuda());
        assertEquals("CUDA", first.getExecutionProvider());
        assertEquals(0, first.getCudaDeviceId());
        assertEquals(4096L, first.getCudaGpuMemLimitMb());
        assertEquals("kSameAsRequested", first.getCudaArenaExtendStrategy());
        assertEquals("HEURISTIC", first.getCudaCudnnConvAlgoSearch());
        assertFalse(first.isCudaDoCopyInDefaultStream());
        assertNotEquals(first.cacheKey(), second.cacheKey());
    }

    @Test
    public void cpuCacheKeyIgnoresDormantCudaValues() {
        JSONObject values = JSON.parseObject("{\"executionProvider\":\"CPU\",\"cudaDeviceId\":7,"
                + "\"cudaGpuMemLimitMb\":1024}");

        assertEquals(OnnxRuntimeConfig.cpu().cacheKey(), OnnxRuntimeConfig.from(values).cacheKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownExecutionProvider() {
        OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"TENSORRT\"}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeCudaDeviceId() {
        OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\",\"cudaDeviceId\":-1}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeGpuMemoryLimit() {
        OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\",\"cudaGpuMemLimitMb\":-1}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownArenaStrategy() {
        OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\","
                + "\"cudaArenaExtendStrategy\":\"grow\"}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownCudnnSearchMode() {
        OnnxRuntimeConfig.from(JSON.parseObject("{\"executionProvider\":\"CUDA\","
                + "\"cudaCudnnConvAlgoSearch\":\"FASTEST\"}"));
    }
}
