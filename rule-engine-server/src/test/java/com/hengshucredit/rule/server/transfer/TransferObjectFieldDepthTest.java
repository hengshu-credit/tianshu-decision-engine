package com.hengshucredit.rule.server.transfer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TransferObjectFieldDepthTest {
    @Test public void structureKeyStorageDoesNotGrowWithAncestorCount() {
        int count = 512;
        var index = TransferObjectFieldIndex.of(configuration(chain(count, 0)));
        long characters = keyCharacters(index, count);
        assertTrue("深链索引不能为每个字段重复缓存完整祖先路径，实际字符数=" + characters,
                characters <= count * 128L);
    }

    @Test public void deepChainsKeepLinearKeysAndMatchReorderedTargetIds() {
        long smaller = verifyDeepChain(4_000);
        long larger = verifyDeepChain(8_000);
        assertEquals("字段数翻倍时结构键总字符数应线性增长", smaller * 2, larger);
    }

    @Test public void changingAnAncestorIsolatesItsWholeBranchWithoutAffectingOtherRoots() {
        int count = 4_000;
        List<Map<String, Object>> source = chain(count, 0);
        List<Map<String, Object>> target = chain(count, 100_000);
        source.add(field(9_000L, null, "other"));
        target.add(field(109_000L, null, "other"));
        target.get(2_000).put("varCode", "changed");
        Collections.reverse(target);
        Map<Long, Long> matches = TransferObjectFieldIndex.of(configuration(source))
                .matchingIds(TransferObjectFieldIndex.of(configuration(target)));
        assertEquals(2_001, matches.size());
        for (long id = 1; id <= count; id++) {
            if (id <= 2_000) assertEquals(Long.valueOf(id + 100_000), matches.get(id));
            else assertFalse(matches.containsKey(id));
        }
        assertEquals(Long.valueOf(109_000), matches.get(9_000L));
    }

    private static long verifyDeepChain(int count) {
        long started = System.nanoTime();
        var source = TransferObjectFieldIndex.of(configuration(chain(count, 0)));
        List<Map<String, Object>> targetFields = chain(count, 100_000);
        Collections.reverse(targetFields);
        var target = TransferObjectFieldIndex.of(configuration(targetFields));
        Map<Long, Long> matches = source.matchingIds(target);
        assertEquals(count, matches.size());
        for (long id = 1; id <= count; id++) assertEquals(Long.valueOf(id + 100_000), matches.get(id));
        long characters = keyCharacters(source, count);
        assertTrue(characters <= count * 128L);
        System.out.printf("深链字段索引 fields=%d keyChars=%d elapsedMs=%d maxHeapBytes=%d%n",
                count, characters, (System.nanoTime() - started) / 1_000_000, Runtime.getRuntime().maxMemory());
        return characters;
    }

    private static long keyCharacters(TransferObjectFieldIndex index, int count) {
        long total = 0;
        for (long id = 1; id <= count; id++) total += index.path(id).length();
        return total;
    }

    private static List<Map<String, Object>> chain(int count, long offset) {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (long id = 1; id <= count; id++) fields.add(field(id + offset, id == 1 ? null : id - 1 + offset, "node"));
        return fields;
    }

    private static Map<String, Object> field(long id, Long parent, String code) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id); value.put("parentFieldId", parent);
        value.put("varCode", code); value.put("varType", "OBJECT"); value.put("sortOrder", 0);
        return value;
    }

    private static Map<String, Object> configuration(List<Map<String, Object>> fields) { return Map.of("fields", fields); }
}
