package com.hengshucredit.rule.server.service.onnx;

import org.junit.Test;
import org.opencv.core.Mat;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import static org.junit.Assert.assertEquals;

public class ImageTensorUtilsTest {

    @Test
    public void decodesPlainBase64AndDataUrl() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("../assets/docs/face.jpg")));

        Mat plain = ImageTensorUtils.decodeBase64(base64);
        Mat dataUrl = ImageTensorUtils.decodeBase64("data:image/jpeg;base64," + base64);
        try {
            assertEquals(750, plain.cols());
            assertEquals(500, plain.rows());
            assertEquals(750, dataUrl.cols());
            assertEquals(500, dataUrl.rows());
        } finally {
            plain.release();
            dataUrl.release();
        }
    }

    @Test
    public void expandedCropIsSquareAndReflectPadsOutsideImage() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("../assets/docs/face.jpg")));
        Mat image = ImageTensorUtils.decodeBase64(base64);
        FaceRegion face = new FaceRegion(100d, 10d, 200d, 100d, 0.9d, null);
        Mat crop = ImageTensorUtils.expandedSquareCrop(image, face, 1.5d);
        try {
            assertEquals(300, crop.cols());
            assertEquals(300, crop.rows());
        } finally {
            crop.release();
            image.release();
        }
    }

    @Test
    public void facenoxBatchUsesRgbChwAndUnitScale() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get("../assets/docs/face.jpg")));
        Mat image = ImageTensorUtils.decodeBase64(base64);
        FaceRegion face = new FaceRegion(270d, 40d, 210d, 260d, 0.9d, null);
        try {
            float[][][][] batch = ImageTensorUtils.facenoxBatch(image, java.util.Collections.singletonList(face));
            assertEquals(1, batch.length);
            assertEquals(3, batch[0].length);
            assertEquals(128, batch[0][0].length);
            assertEquals(128, batch[0][0][0].length);
        } finally {
            image.release();
        }
    }
}
