package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.core.function.ImageInputFunctions;
import com.hengshucredit.rule.model.entity.RuleExternalApiConfig;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 显式文件入参转换。预览只有占位值；下载在完整调用的复用边界内、鉴权前执行。 */
public final class ExternalApiFileInputs {
    private ExternalApiFileInputs() { }
    public record Prepared(Map<String, Object> params, List<Map<String, Object>> files) { }
    public static class FileInputException extends IllegalArgumentException {
        FileInputException(String message, Throwable cause) { super(message, cause); }
    }

    static void validate(JSONObject field) {
        JSONObject config = field.getJSONObject("file");
        if (config == null) return;
        if (!Set.of("JSON", "FORM", "FORM_DATA").contains(field.getString("location"))) throw new IllegalArgumentException("文件转换只能用于请求体字段");
        if (!Set.of("BASE64", "ZIP_BASE64").contains(config.getString("mode"))) throw new IllegalArgumentException("文件转换方式必须为 Base64 或 ZIP + Base64");
        String kind = config.getString("kind");
        if (kind != null && !Set.of("PDF", "IMAGE", "ANY").contains(kind)) throw new IllegalArgumentException("文件类型必须为 PDF、图片或文件");
        int limit = limit(config);
        if (limit < 1 || limit > ImageInputFunctions.MAX_IMAGE_BYTES) throw new IllegalArgumentException("文件大小上限必须在 1 字节到 10 MB 之间");
        if (config.getInteger("maxDimension") != null && (config.getIntValue("maxDimension") < 1 || config.getIntValue("maxDimension") > 65535)) throw new IllegalArgumentException("图片尺寸上限必须在 1 到 65535 像素之间");
        String name = config.getString("name");
        if (name != null && (name.isBlank() || name.contains("/") || name.contains("\\") || name.equals(".."))) throw new IllegalArgumentException("ZIP 内文件名不能包含目录");
    }

    public static Prepared prepare(RuleExternalApiConfig api, Map<String, Object> input, boolean preview) {
        List<JSONObject> fields = ExternalApiRequestPlan.fields(ExternalApiRequestPlan.specification(api));
        if (fields.stream().noneMatch(field -> field.get("file") != null)) return new Prepared(input, List.of());
        Map<String, Object> params = new LinkedHashMap<>(input);
        Map<String, Object> values = new LinkedHashMap<>();
        if (input.get("__apiFields") instanceof Map<?, ?> supplied) supplied.forEach((id, value) -> values.put(String.valueOf(id), value));
        Map<String, byte[]> downloaded = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (JSONObject field : fields) {
            JSONObject config = field.getJSONObject("file");
            String id = field.getString("id");
            if (config == null || !values.containsKey(id) || values.get(id) == null || "".equals(values.get(id))) continue;
            try {
                validate(field);
                String source = String.valueOf(values.get(id));
                URI uri = URI.create(source);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("文件取值必须是合法 HTTP(S) 地址");
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("fieldId", id); metadata.put("path", field.getString("path")); metadata.put("mode", config.getString("mode")); metadata.put("preview", preview);
                if (preview) {
                    values.put(id, "[预览占位：执行时下载文件并转换为 " + config.getString("mode") + "]");
                } else {
                    byte[] bytes = downloaded.get(source);
                    if (bytes == null) {
                        int remaining = Math.min(300000, RequestDeadlineContext.remainingMillis());
                        if (remaining < 100) throw new IllegalArgumentException("文件下载已超过调用总超时");
                        bytes = Base64.getDecoder().decode(new ImageInputFunctions().imageToBase64(source, remaining));
                        downloaded.put(source, bytes);
                    }
                    validateContent(bytes, config);
                    if ("ZIP_BASE64".equals(config.getString("mode"))) bytes = zip(bytes, config.getString("name") == null ? "document.pdf" : config.getString("name"));
                    if (bytes.length > limit(config)) throw new IllegalArgumentException("转换后文件超过 " + limit(config) + " 字节上限");
                    values.put(id, Base64.getEncoder().encodeToString(bytes));
                    metadata.put("bytes", bytes.length);
                }
                files.add(metadata);
            } catch (Exception error) {
                throw new FileInputException("请求文件 " + field.getString("path") + " 校验失败：" + error.getMessage(), error);
            }
        }
        params.put("__apiFields", values);
        return new Prepared(params, files);
    }

    static void validateContent(byte[] bytes, JSONObject config) throws java.io.IOException {
        if (bytes.length == 0 || bytes.length > limit(config)) throw new IllegalArgumentException("文件不能为空且不能超过 " + limit(config) + " 字节");
        if ("PDF".equals(config.getString("kind")) && (bytes.length < 5 || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))) throw new IllegalArgumentException("文件内容不是 PDF");
        if ("IMAGE".equals(config.getString("kind"))) {
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("文件内容不是支持的图片格式");
                var reader = readers.next();
                try {
                    reader.setInput(stream);
                    Integer maximum = config.getInteger("maxDimension");
                    if (maximum != null && (reader.getWidth(0) > maximum || reader.getHeight(0) > maximum)) throw new IllegalArgumentException("图片宽高不能超过 " + maximum + " 像素");
                } finally { reader.dispose(); }
            }
        }
    }

    private static int limit(JSONObject config) { return config.getInteger("maxBytes") == null ? ImageInputFunctions.MAX_IMAGE_BYTES : config.getIntValue("maxBytes"); }
    private static byte[] zip(byte[] bytes, String name) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) { ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry(); }
        return output.toByteArray();
    }
}
