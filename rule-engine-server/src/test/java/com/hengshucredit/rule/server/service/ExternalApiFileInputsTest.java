package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.Assert.*;

public class ExternalApiFileInputsTest {
    @Test public void validatesImageDimensionsBeforeProviderSubmissionAndDoesNotAlterPdfBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1081, 1, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var config = JSON.parseObject("{\"kind\":\"IMAGE\",\"maxBytes\":307200,\"maxDimension\":1080}");
        assertThrows(IllegalArgumentException.class, () -> ExternalApiFileInputs.validateContent(bytes.toByteArray(), config));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiFileInputs.validateContent("not a PDF".getBytes(), JSON.parseObject("{\"kind\":\"PDF\"}")));
    }
    @Test public void zipEntryNamesCannotEscapeTheirArchiveAndInvalidModesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ExternalApiFileInputs.validate(JSON.parseObject("{\"location\":\"JSON\",\"file\":{\"mode\":\"ZIP_BASE64\",\"name\":\"../document.pdf\"}}")));
        assertThrows(IllegalArgumentException.class, () -> ExternalApiFileInputs.validate(JSON.parseObject("{\"location\":\"HEADER\",\"file\":{\"mode\":\"BASE64\"}}")));
    }
}
