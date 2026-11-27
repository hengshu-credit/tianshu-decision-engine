package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class TransferProjectSetTest {
    private static byte[] archive(String declared, byte[] member) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            add(zip, "project-set.json", CanonicalJson.writeBytes(Map.of("format","TIANSHU_PROJECT_SET_V1",
                    "projects", List.of(Map.of("code","UNIT_PROJECT","sha256",declared)))));
            add(zip, "projects/1.zip", member);
        }
        return out.toByteArray();
    }
    private static void add(ZipOutputStream zip, String path, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        entry.setMethod(ZipEntry.STORED); entry.setSize(bytes.length); entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    @Test public void checksMemberDigestAndPreviewIdentityBeforeUsingAnyService() throws Exception {
        byte[] member = {1,2,3};
        var service = new TransferProjectSetService(null, null, null, null);
        var corrupt = new MockMultipartFile("file", archive("f".repeat(64), member));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.preview(corrupt)).getMessage().contains("子包摘要"));
        var changed = new MockMultipartFile("file", archive(Sha256Digests.bytes(member), member));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.apply(changed, "0".repeat(64), "tester"))
                .getMessage().contains("重新预检"));
    }

    @Test public void includesCaseSettingsInCheckedFormatAndNeverBindsADifferentSnapshot() {
        var codec = new TransferBundleCodec();
        var model = new TransferBundle.Resource("MODEL:81", Map.of("_transferSourceModelVersion", 42,
                "outputFields", List.of(Map.of("id", 7))), List.of(), List.of(), List.of(),
                List.of(Map.of("code", "CHECK", "expectedJson", "[]")));
        var bundle = codec.decodeDeferred(codec.encode(new TransferBundle(List.of(model.key()), List.of(model), List.of()))).bundle();
        assertEquals("CHECK", bundle.resources().get(0).pageSettings().get(0).get("code"));
        var selected = TransferSelection.index(bundle.resources());
        assertEquals(model.key(), TransferVersionService.includedModel(
                Map.of("refType","MODEL_OUTPUT","ownerId",81,"versionId",42,"versionSource","SNAPSHOT","refId",7), selected));
        assertNull(TransferVersionService.includedModel(
                Map.of("refType","MODEL_OUTPUT","ownerId",81,"versionId",41,"versionSource","SNAPSHOT","refId",7), selected));
    }
}
