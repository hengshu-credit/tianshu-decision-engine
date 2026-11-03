package com.hengshucredit.rule.server.system;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded OpenType reader. Collections are repacked as standalone SFNT faces for browsers. */
final class EngineFontFile {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    record Instance(String name, Map<String, Double> coordinates) {}
    record Face(String name, List<Instance> instances) {}
    private record Table(String tag, int offset, int length) {}
    private final byte[] bytes;
    private final ByteBuffer data;
    private final List<Integer> offsets = new ArrayList<>();

    EngineFontFile(byte[] bytes) {
        if (bytes.length < 12 || bytes.length > MAX_BYTES) throw invalid();
        this.bytes = bytes; data = ByteBuffer.wrap(bytes);
        if (data.getInt(0) == 0x74746366) {
            int count = integer(8);
            if (count < 1 || count > 64) throw invalid();
            range(12, count * 4);
            for (int i = 0; i < count; i++) offsets.add(integer(12 + i * 4));
        } else offsets.add(0);
        for (int offset : offsets) tables(offset);
    }

    List<Face> faces() {
        List<Face> result = new ArrayList<>();
        for (int offset : offsets) {
            Map<String, Table> tables = tables(offset);
            Map<Integer, String> names = names(tables.get("name"));
            List<Instance> instances = new ArrayList<>();
            instances.add(new Instance("默认", Map.of()));
            Table variable = tables.get("fvar");
            if (variable != null) {
                rangeIn(variable, 0, 16);
                int p = variable.offset, start = ushort(p + 4), count = ushort(p + 8), size = ushort(p + 10);
                int n = ushort(p + 12), step = ushort(p + 14);
                if (count > 32 || n > 256 || size < 20 || step < 4 + count * 4) throw invalid();
                rangeIn(variable, start, count * size + n * step);
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String tag = tag(p + start + i * size);
                    if (!tag.matches("[A-Za-z0-9 ]{4}")) throw invalid();
                    tags.add(tag);
                }
                for (int i = 0; i < n; i++) {
                    int q = p + start + count * size + i * step;
                    Map<String, Double> coordinates = new LinkedHashMap<>();
                    for (int j = 0; j < count; j++) coordinates.put(tags.get(j), data.getInt(q + 4 + j * 4) / 65536.0);
                    instances.add(new Instance(names.getOrDefault(ushort(q), "实例 " + (i + 1)), coordinates));
                }
            }
            result.add(new Face(names.getOrDefault(4, names.getOrDefault(1, "字体 " + (result.size() + 1))), instances));
        }
        return result;
    }

    void validate() {
        faces();
        for (int i = 0; i < offsets.size(); i++) {
            try { Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(face(i))); }
            catch (java.awt.FontFormatException | java.io.IOException error) { throw invalid(); }
        }
    }

    byte[] face(int index) {
        if (index < 0 || index >= offsets.size()) throw invalid();
        int offset = offsets.get(index);
        var tables = new ArrayList<>(tables(offset).values());
        boolean invalidStat = invalidStat(tables(offset).get("STAT"));
        if (data.getInt(0) != 0x74746366 && !invalidStat) return bytes.clone();
        // A malformed optional STAT table can prevent browsers loading otherwise valid outlines/fvar instances.
        tables.removeIf(table -> "DSIG".equals(table.tag) || invalidStat && "STAT".equals(table.tag));
        tables.sort(java.util.Comparator.comparing(Table::tag));
        int length = 12 + tables.size() * 16;
        for (Table table : tables) { length += (table.length + 3) & ~3; if (length > MAX_BYTES) throw invalid(); }
        byte[] output = new byte[length]; ByteBuffer out = ByteBuffer.wrap(output);
        out.putInt(data.getInt(offset)).putShort((short) tables.size());
        int power = Integer.highestOneBit(tables.size());
        out.putShort((short) (power * 16)).putShort((short) Integer.numberOfTrailingZeros(power)).putShort((short) (tables.size() * 16 - power * 16));
        int target = 12 + tables.size() * 16, head = -1;
        for (int i = 0; i < tables.size(); i++) {
            Table table = tables.get(i);
            System.arraycopy(bytes, table.offset, output, target, table.length);
            if ("head".equals(table.tag)) { if (table.length < 12) throw invalid(); head = target; out.putInt(head + 8, 0); }
            out.position(12 + i * 16);
            out.put(table.tag.getBytes(StandardCharsets.US_ASCII)).putInt(checksum(output, target, (table.length + 3) & ~3)).putInt(target).putInt(table.length);
            target += (table.length + 3) & ~3;
        }
        if (head < 0) throw invalid();
        out.putInt(head + 8, (int) (0xB1B0AFBAL - Integer.toUnsignedLong(checksum(output, 0, output.length))));
        return output;
    }

    private boolean invalidStat(Table table) {
        if (table == null) return false;
        try {
            rangeIn(table, 0, 18);
            int p = table.offset, axes = ushort(p + 6), count = ushort(p + 12), values = integer(p + 14);
            rangeIn(table, integer(p + 8), axes * ushort(p + 4));
            rangeIn(table, values, count * 2);
            for (int i = 0; i < count; i++) {
                int relative = values + ushort(p + values + i * 2);
                rangeIn(table, relative, 4);
                int q = p + relative, format = ushort(q);
                if (format >= 1 && format <= 3) {
                    if (ushort(q + 2) >= axes) return true;
                } else if (format == 4) {
                    int n = ushort(q + 2); rangeIn(table, relative, 8 + n * 6);
                    for (int j = 0; j < n; j++) if (ushort(q + 8 + j * 6) >= axes) return true;
                }
            }
            return false;
        } catch (IllegalArgumentException error) { return true; }
    }

    private static int checksum(byte[] bytes, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes); int sum = 0;
        for (int i = offset; i < offset + length; i += 4) sum += buffer.getInt(i);
        return sum;
    }
    private Map<String, Table> tables(int offset) {
        range(offset, 12);
        int magic = data.getInt(offset);
        if (magic != 0x00010000 && magic != 0x4f54544f && magic != 0x74727565) throw invalid();
        int count = ushort(offset + 4);
        if (count < 1 || count > 256) throw invalid();
        range(offset + 12, count * 16);
        Map<String, Table> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int p = offset + 12 + i * 16;
            Table table = new Table(tag(p), integer(p + 8), integer(p + 12)); range(table.offset, table.length);
            if (result.put(table.tag, table) != null) throw invalid();
        }
        if (!result.containsKey("name") || !result.containsKey("head") || !result.containsKey("cmap")) throw invalid();
        return result;
    }
    private Map<Integer, String> names(Table table) {
        rangeIn(table, 0, 6);
        int p = table.offset, count = ushort(p + 2), strings = ushort(p + 4);
        rangeIn(table, 6, count * 12);
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            int q = p + 6 + i * 12, platform = ushort(q), language = ushort(q + 4);
            int id = ushort(q + 6), length = ushort(q + 8), start = strings + ushort(q + 10);
            rangeIn(table, start, length);
            if (platform != 0 && platform != 3) continue;
            String value = new String(bytes, p + start, length, StandardCharsets.UTF_16BE).replace("\u0000", "");
            if (!value.isBlank() && value.length() <= 256 && (!result.containsKey(id) || language == 0x804)) result.put(id, value);
        }
        return result;
    }
    private void rangeIn(Table table, int offset, int length) { if (table == null || offset < 0 || length < 0 || offset > table.length - length) throw invalid(); }
    private void range(int offset, int length) { if (offset < 0 || length < 0 || offset > bytes.length - length) throw invalid(); }
    private int integer(int offset) { range(offset, 4); int value = data.getInt(offset); if (value < 0) throw invalid(); return value; }
    private int ushort(int offset) { range(offset, 2); return Short.toUnsignedInt(data.getShort(offset)); }
    private String tag(int offset) { range(offset, 4); return new String(bytes, offset, 4, StandardCharsets.US_ASCII); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("字体无效或过大，请上传 32 MB 以内的 TTF、OTF、TTC 或 OTC 字体"); }
}
