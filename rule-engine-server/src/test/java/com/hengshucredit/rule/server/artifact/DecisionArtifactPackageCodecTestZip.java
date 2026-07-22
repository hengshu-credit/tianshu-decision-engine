package com.hengshucredit.rule.server.artifact;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class DecisionArtifactPackageCodecTestZip {
    private DecisionArtifactPackageCodecTestZip() {
    }

    static Map<String, byte[]> read(byte[] source) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), input.readAllBytes());
            }
            return entries;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] write(Map<String, byte[]> entries) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(buffer)) {
            for (Map.Entry<String, byte[]> source : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(source.getKey());
                entry.setTime(0L);
                output.putNextEntry(entry);
                output.write(source.getValue());
                output.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return buffer.toByteArray();
    }
}
