package com.hengshucredit.rule.core.function;

import com.hengshucredit.rule.core.engine.QLExpressEngine;
import org.junit.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class ImageGeometryFunctionsTest {
    private final ImageGeometryFunctions images = new ImageGeometryFunctions();

    @Test public void channelOrderAndNormalizationFollowTheScript() throws Exception {
        BufferedImage sample = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        sample.setRGB(0, 0, 0xFF0000);
        sample.setRGB(1, 0, 0x0000FF);
        var out = new ByteArrayOutputStream();
        ImageIO.write(sample, "png", out);
        String encoded = Base64.getEncoder().encodeToString(out.toByteArray());
        assertEquals(Map.of("width", 2, "height", 1), images.imageDimensions(encoded));
        var tensor = images.imageTensorChw(encoded, Map.of("width", 2, "height", 1, "order", "RGB", "offset", 0, "divisor", 255));
        assertEquals(List.of(List.of(List.of(1f, 0f)), List.of(List.of(0f, 0f)), List.of(List.of(0f, 1f))), tensor);
        assertThrows(IllegalArgumentException.class, () -> images.imageTensorChw(encoded, Map.of("width", 16384, "height", 16384)));
    }

    @Test public void transformedPointsAndCoverageUseSourceToTargetCoordinates() {
        var matrix = images.pointSimilarityAffine(List.of(List.of(0, 0), List.of(1, 0)),
                List.of(List.of(1, 0), List.of(2, 0)));
        var size = Map.of("width", 4, "height", 2);
        assertEquals(.75, images.imageAffineCoverage(size, matrix, size), 1e-8);
        assertThrows(IllegalArgumentException.class, () -> images.pointSimilarityAffine(List.of(List.of(1, 1), List.of(1, 1)),
                List.of(List.of(0, 0), List.of(1, 1))));
    }

    @Test public void scriptInputRejectionReportsTheConfiguredMessage() {
        var result = new QLExpressEngine().execute("requireCondition(isFiniteNumber(null), '需要有限数值')", new java.util.HashMap<>(), false);
        assertFalse(result.isSuccess());
        assertEquals("需要有限数值", result.getErrorMessage());
    }
}
