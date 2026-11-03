package com.hengshucredit.rule.server.system;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class EngineFontFileTest {
    @Test public void dropsOnlyInvalidOptionalStyleMetadataWhilePreservingFaceTables() {
        String[] tags = {"head", "name", "cmap", "STAT"};
        int[] lengths = {54, 6, 4, 48};
        byte[] bytes = new byte[192]; ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(0x10000).putShort((short) 4);
        int offset = 76;
        for (int i = 0; i < tags.length; i++) {
            buffer.position(12 + i * 16);
            buffer.put(tags[i].getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(0).putInt(offset).putInt(lengths[i]);
            if (tags[i].equals("STAT")) {
                buffer.putShort(offset, (short) 1).putShort(offset + 4, (short) 8).putShort(offset + 6, (short) 2);
                buffer.putInt(offset + 8, 18).putShort(offset + 12, (short) 1).putInt(offset + 14, 34);
                buffer.putShort(offset + 34, (short) 2).putShort(offset + 36, (short) 1).putShort(offset + 38, (short) 2);
            }
            offset += (lengths[i] + 3) & ~3;
        }
        byte[] repaired = new EngineFontFile(bytes).face(0);
        assertEquals(3, ByteBuffer.wrap(repaired).getShort(4));
        assertEquals(new EngineFontFile(bytes).faces(), new EngineFontFile(repaired).faces());
        assertEquals(4, ByteBuffer.wrap(bytes).getShort(4)); // original upload is retained untouched
    }

    @Test public void collectionExtractsBothFacesWithRebasedOffsetsAndValidChecksums() throws Exception {
        byte[] font = Files.readAllBytes(Path.of("../rule-engine-web/public/fonts/element-icons.ttf"));
        int size = (font.length + 3) & ~3;
        byte[] collection = new byte[20 + size * 2];
        ByteBuffer buffer = ByteBuffer.wrap(collection);
        buffer.putInt(0x74746366).putInt(0x00010000).putInt(2).putInt(20).putInt(20 + size);
        for (int base : new int[]{20, 20 + size}) {
            System.arraycopy(font, 0, collection, base, font.length);
            int tables = Short.toUnsignedInt(buffer.getShort(base + 4));
            for (int i = 0; i < tables; i++) {
                int offset = base + 12 + i * 16 + 8;
                buffer.putInt(offset, buffer.getInt(offset) + base);
            }
        }
        EngineFontFile parsed = new EngineFontFile(collection);
        parsed.validate();
        assertEquals(2, parsed.faces().size());
        for (int i = 0; i < 2; i++) {
            byte[] face = parsed.face(i);
            new EngineFontFile(face).validate();
            assertEquals(parsed.faces().get(i).name(), new EngineFontFile(face).faces().get(0).name());
            ByteBuffer data = ByteBuffer.wrap(face); int sum = 0;
            for (int p = 0; p < face.length; p += 4) sum += data.getInt(p);
            assertEquals(0xB1B0AFBA, sum);
        }
        assertThrows(IllegalArgumentException.class, () -> parsed.face(2));
        buffer.putInt(12, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> new EngineFontFile(collection));
    }

    @Test public void rejectsTruncatedTablesAndExcessiveCollectionCounts() {
        byte[] bytes = new byte[20]; ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putInt(0x74746366).putInt(0x10000).putInt(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> new EngineFontFile(bytes));
        buffer.putInt(0, 0x10000); buffer.putShort(4, (short) 100);
        assertThrows(IllegalArgumentException.class, () -> new EngineFontFile(bytes));
    }
}
