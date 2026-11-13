package com.hengshucredit.rule.server.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 以路径数组记录文件位置，与用户 JSON 的属性名完全隔离；恢复时仍履行 MultipartFile 契约。 */
final class ExecutionRequestSnapshot {
    private ExecutionRequestSnapshot() {}

    record Snapshot(Map<String,Object> params, List<List<Object>> files) {
        Map<String,Object> checkpoint() {
            var result = new LinkedHashMap<String,Object>();
            result.put("version", 3);
            result.put("requestParams", params);
            if (!files.isEmpty()) result.put("requestFiles", files);
            return result;
        }
        Object digestInput() { return files.isEmpty() ? params : checkpoint(); }
    }

    static Snapshot capture(Map<String,Object> params) {
        var files = new ArrayList<List<Object>>();
        Map<String,Object> result = params == null ? Map.of() : params;
        if (containsFile(result)) {
            @SuppressWarnings("unchecked")
            var copy = (Map<String,Object>) encode(result, new ArrayList<>(), files);
            result = copy;
        }
        return new Snapshot(result, files);
    }

    private static boolean containsFile(Object value) {
        if (value instanceof MultipartFile) return true;
        if (value instanceof Map<?,?> map) return map.values().stream().anyMatch(ExecutionRequestSnapshot::containsFile);
        if (value instanceof List<?> list) return list.stream().anyMatch(ExecutionRequestSnapshot::containsFile);
        return false;
    }

    private static Object encode(Object value, List<Object> path, List<List<Object>> files) {
        if (value instanceof MultipartFile file) {
            files.add(List.copyOf(path));
            try {
                var data = new LinkedHashMap<String,Object>();
                data.put("name", file.getName());
                data.put("filename", file.getOriginalFilename());
                data.put("type", file.getContentType());
                data.put("bytes", Base64.getEncoder().encodeToString(file.getBytes()));
                return data;
            } catch (IOException failure) { throw new IllegalArgumentException("无法读取上传文件", failure); }
        }
        if (value instanceof Map<?,?> map) {
            Map<String,Object> copy = new java.util.TreeMap<>();
            for (var entry : map.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
            copy.replaceAll((key, item) -> encodeChild(key, item, path, files));
            return copy;
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) copy.add(encodeChild(i, list.get(i), path, files));
            return copy;
        }
        return value;
    }

    private static Object encodeChild(Object key, Object value, List<Object> path, List<List<Object>> files) {
        path.add(key);
        Object stored = encode(value, path, files);
        path.remove(path.size()-1);
        return stored;
    }

    @SuppressWarnings("unchecked")
    static Map<String,Object> restore(Map<String,Object> params, List<?> files) {
        if (files == null) return params;
        for (Object location : files) {
            var path = (List<?>) location;
            Object parent = params;
            for (Object key : path.subList(0, path.size()-1))
                parent = key instanceof Number n ? ((List<?>) parent).get(n.intValue()) : ((Map<?,?>) parent).get(key);
            Object key = path.get(path.size()-1);
            if (key instanceof Number index) {
                var list = (List<Object>) parent;
                list.set(index.intValue(), file((Map<?,?>) list.get(index.intValue())));
            } else {
                var map = (Map<String,Object>) parent;
                map.put((String)key, file((Map<?,?>)map.get(key)));
            }
        }
        return params;
    }

    private static MultipartFile file(Map<?,?> value) {
        return new StoredFile((String)value.get("name"), (String)value.get("filename"), (String)value.get("type"),
                Base64.getDecoder().decode((String)value.get("bytes")));
    }

    private record StoredFile(String name, String filename, String type, byte[] bytes) implements MultipartFile {
        public String getName() { return name; }
        public String getOriginalFilename() { return filename; }
        public String getContentType() { return type; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), bytes); }
    }
}
