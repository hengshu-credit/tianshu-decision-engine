package com.hengshucredit.rule.server.transfer;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class TransferModelFieldsTest {
    @Test public void mapsSourceOutputToActualAppliedFieldEvenWhenModelAndFieldIdsOverlap() {
        var source = model(3L);
        var incoming = TransferBundle.copyConfiguration(source);
        TransferModelFields.prepare(incoming,null);
        assertFalse(((Map<?,?>)((List<?>)incoming.get("outputFields")).get(0)).containsKey("id"));
        assertEquals("/outputFields/0/id",TransferModelFields.outputPath(source,3L));
        assertEquals(Map.of(3L,88L),TransferModelFields.outputMapping(source,model(88L)));
        assertThrows(IllegalArgumentException.class,()->TransferModelFields.outputPath(source,88L));
    }

    private static Map<String,Object> model(long outputId) {
        return new LinkedHashMap<>(Map.of("outputFields",List.of(new LinkedHashMap<>(Map.of(
                "id",outputId,"modelId",3L,"fieldName","faces","fieldType","LIST","featureName","faces")))));
    }
}
