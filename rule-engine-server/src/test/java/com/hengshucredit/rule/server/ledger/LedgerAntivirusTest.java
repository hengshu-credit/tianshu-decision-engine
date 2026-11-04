package com.hengshucredit.rule.server.ledger;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import static org.junit.Assert.*;

public class LedgerAntivirusTest {
    private LedgerAntivirus.Result scan(String response, boolean terminated) throws Exception {
        try (var server=new ServerSocket(0)) {
            var executor=Executors.newSingleThreadExecutor();
            try {
                var received=executor.submit(()->{try(var socket=server.accept()) {
                    var in=new DataInputStream(socket.getInputStream());var command=new ByteArrayOutputStream();int value;
                    while((value=in.read())!=0){if(value<0)throw new IllegalStateException("Incomplete command");command.write(value);}
                    assertEquals("zINSTREAM",command.toString(StandardCharsets.US_ASCII));
                    var bytes=new ByteArrayOutputStream();int length;
                    while((length=in.readInt())!=0){assertTrue(length>0&&length<=8192);bytes.write(in.readNBytes(length));}
                    socket.getOutputStream().write((response+(terminated?"\0":"")).getBytes(StandardCharsets.UTF_8));return bytes.toByteArray();
                }});
                var properties=new LedgerProperties();properties.setAntivirusHost("127.0.0.1");properties.setAntivirusPort(server.getLocalPort());properties.setAntivirusTimeoutMs(1000);
                byte[] input="scan exact bytes".getBytes(StandardCharsets.UTF_8);var result=new LedgerAntivirus(properties).scan(input);
                assertArrayEquals(input,received.get());return result;
            } finally { executor.shutdownNow(); }
        }
    }
    @Test public void onlyExplicitCompleteOkResponseIsClean() throws Exception {
        assertEquals("CLEAN",scan("stream: OK",true).status());
        assertEquals("PENDING",scan("stream: OK",false).status());
        assertEquals("PENDING",scan("stream: NOT OK",true).status());
        assertEquals("PENDING",scan("INSTREAM size limit exceeded. ERROR",true).status());
    }
    @Test public void threatsAreQuarantinedAndMissingScannerNeverBypassesChecking() throws Exception {
        assertEquals("QUARANTINED",scan("stream: Test.Signature FOUND",true).status());
        assertEquals("PENDING",new LedgerAntivirus(new LedgerProperties()).scan(new byte[]{1}).status());
    }
    @Test public void rejectsForgedExtensionsControlCharactersAndOversizedPixelHeaders() throws Exception {
        assertThrows(IllegalArgumentException.class,()->LedgerAssetService.detect("not an image".getBytes(StandardCharsets.UTF_8),"sample.png"));
        assertThrows(IllegalArgumentException.class,()->LedgerAssetService.filename("x\r\ny\n.png"));
        assertThrows(IllegalArgumentException.class,()->LedgerAssetService.filename("../test.png"));
        var image=new java.awt.image.BufferedImage(4097,1,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var encoded=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",encoded);
        assertThrows(IllegalArgumentException.class,()->LedgerAssetService.normalizeImage(encoded.toByteArray()));
    }
}
