package com.hengshucredit.rule.server.functions;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import com.hengshucredit.rule.core.engine.RequestContext;
import com.hengshucredit.rule.model.dto.RuleResult;
import com.hengshucredit.rule.model.entity.RuleFunction;
import com.hengshucredit.rule.server.service.FunctionRegistrar;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GeoFunctionsTest {
    private final GeoFunctions functions = new GeoFunctions();

    @Test
    public void measuresZeroOneKilometerAndAntimeridianSymmetrically() {
        double delta = Math.toDegrees(1000.0 / 6371008.8);
        assertEquals(0.0, functions.distanceMeters(116.397, 39.908, 116.397, 39.908), 0.0);
        assertEquals(1000.0, functions.distanceMeters(0, 0, delta, 0), 0.000001);
        assertEquals(1000.0, functions.distanceMeters(0, delta, 0, 0), 0.000001);
        assertEquals(functions.distanceMeters(179.999, 0, -179.999, 0),
                functions.distanceMeters(-179.999, 0, 179.999, 0), 0.000001);
        assertTrue(functions.distanceMeters(179.999, 0, -179.999, 0) < 1000);
    }

    @Test
    public void countsDistinctPeopleForTheDateIncludingTheOneKilometerBoundary() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < 4; index++) rows.add(record("P" + index, "2026-09-21", 0));
        rows.add(record("BOUNDARY", "2026-09-21", 1000));
        rows.add(record("BOUNDARY", "2026-09-21", 10));
        rows.add(record("OLD", "2026-09-20", 0));
        rows.add(record("OUTSIDE", "2026-09-21", 1000.01));
        assertEquals(5, functions.dailyApplicantCount(0, 0, rows, "2026-09-21"));
        rows.add(record("SIXTH", "2026-09-21", 500));
        assertEquals(6, functions.dailyApplicantCount(0, 0, rows, "2026-09-21"));
        assertEquals(1, functions.dailyApplicantCount(0, 0, rows, "2026-09-20"));
        assertEquals(0, functions.dailyApplicantCount(0, 0, List.of(), "2026-09-21"));
    }

    @Test
    public void invalidCoordinatesAndMalformedSameDayRecordsDoNotBecomeZeroCount() {
        for (double longitude : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -180.01, 180.01}) {
            assertThrows(IllegalArgumentException.class, () -> functions.distanceMeters(longitude, 0, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> functions.distanceMeters(0, 90.01, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> functions.dailyApplicantCount(0, 0, "[]", "2026-09-21"));
        assertThrows(IllegalArgumentException.class, () -> functions.dailyApplicantCount(0, 0,
                List.of(Map.of("进件日期", "2026-09-21", "经度", 0, "纬度", 0)), "2026-09-21"));
    }

    @Test
    public void configuredBeanExecutesThroughTheExistingFunctionRegistrationContract() {
        try (StaticApplicationContext beans = new StaticApplicationContext()) {
            beans.getBeanFactory().registerSingleton("geoFunctions", functions);
            FunctionRegistrar registrar = new FunctionRegistrar();
            ReflectionTestUtils.setField(registrar, "applicationContext", beans);
            RuleFunction function = new RuleFunction();
            function.setFuncCode("nearbyCount");
            function.setImplType("BEAN");
            function.setImplBeanName("geoFunctions");
            function.setImplMethod("dailyApplicantCount");
            function.setParamsJson("[{\"name\":\"longitude\",\"type\":\"NUMBER\"},"
                    + "{\"name\":\"latitude\",\"type\":\"NUMBER\"},"
                    + "{\"name\":\"records\",\"type\":\"OBJECT\"},"
                    + "{\"name\":\"date\",\"type\":\"STRING\"}]");
            QLExpressEngine engine = new QLExpressEngine();
            RequestContext request = new RequestContext();
            try (var ignored = request.bindFunctions(registrar.prepareFunctions(List.of(function), engine.getRunner()))) {
                RuleResult result = engine.execute(engine.prepare("return nearbyCount(0, 0, records, \"2026-09-21\");"),
                        Map.of("records", List.of(record("A", "2026-09-21", 1000))), false, request);
                assertTrue(result.getErrorMessage(), result.isSuccess());
                assertEquals(1, ((Number) result.getResult()).intValue());
            }
        }
    }

    private Map<String, Object> record(String identity, String date, double distance) {
        return Map.of("身份证", identity, "进件日期", date,
                "经度", Math.toDegrees(distance / 6371008.8), "纬度", 0);
    }
}
