package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.artifact.DecisionArtifactPackage;
import com.hengshucredit.rule.server.artifact.DecisionArtifactPackageCodec;
import com.hengshucredit.rule.server.artifact.Sha256Digests;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class MysqlModelBlobStoreIntegrationTest {
    /** Run only against a disposable database created on the loopback test MySQL. */
    @Test(timeout=180000)
    public void wholeBlobUnlessServerLimitRequiresSplittingAndArchivesSharePayloads() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("modelFiles.integration"));
        var admin=(DriverManagerDataSource)BillingSummaryCurrencyIntegrationTest.localDataSource();
        String name="codex_object_"+UUID.randomUUID().toString().replace("-","");
        var control=new JdbcTemplate(admin);
        control.execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4");
        try {
            String url=admin.getUrl();
            int query=url.indexOf('?');
            var ds=new DriverManagerDataSource(url.substring(0,url.lastIndexOf('/',query)+1)+name+url.substring(query)+"&maxAllowedPacket=4194304",admin.getUsername(),admin.getPassword());
            new ResourceDatabasePopulator(new ClassPathResource("sql/migrations/2026-11-20-shared-model-files.sql")).execute(ds);
            var db=new JdbcTemplate(ds);
            var manager=new DataSourceTransactionManager(ds);
            var a=new MysqlModelBlobStore(db,manager);
            var b=new MysqlModelBlobStore(new JdbcTemplate(ds),new DataSourceTransactionManager(ds));
            byte[] random=new byte[24 * 1024 * 1024];
            new Random(251).nextBytes(random);
            byte[] bytes=("{\"weights\":\""+Base64.getEncoder().encodeToString(random)+"\"}").getBytes(StandardCharsets.UTF_8);
            String hash=Sha256Digests.bytes(bytes);
            String first="ts-file-v1:models/1/"+hash.substring(0,2)+"/a--"+hash+".onnx";
            String second=first.replace("models/1","models/2").replace("/a--","/b--");
            var executor=Executors.newFixedThreadPool(2);
            try {
                var upload=executor.submit(()->a.put(first,hash,bytes,false));
                var concurrent=executor.submit(()->b.put(second,hash,bytes,false));
                upload.get();concurrent.get();
            } finally {executor.shutdownNow();}
            assertEquals(1,(int)db.queryForObject("SELECT COUNT(*) FROM rule_file_object",Integer.class));
            assertEquals(2,(int)db.queryForObject("SELECT COUNT(*) FROM rule_file_reference",Integer.class));
            assertArrayEquals(bytes,b.read(first,hash,bytes.length));
            assertThrows(IllegalStateException.class,()->a.read(first.replace("models/1","models/3"),hash,bytes.length));
            long maxPayload = db.queryForObject("SELECT @@session.max_allowed_packet",Long.class) - 256;
            long stored = db.queryForObject("SELECT stored_size FROM rule_file_object WHERE digest=?",Long.class,hash);
            assertTrue(stored > 16L * 1024 * 1024); // Exceeds MEDIUMBLOB and the test reader's packet budget.
            int payloadChunks=db.queryForObject("SELECT COUNT(*) FROM rule_file_object_part WHERE object_digest=?",Integer.class,hash);
            assertEquals((stored + maxPayload - 1) / maxPayload, payloadChunks);
            assertTrue(db.queryForObject("SELECT MAX(OCTET_LENGTH(content)) FROM rule_file_chunk",Long.class)<=maxPayload);
            System.out.println("MODEL_STORE raw=" + bytes.length + " stored=" + stored + " maxPayload=" + maxPayload + " rows=" + payloadChunks);
            var pkg=new DecisionArtifactPackage();
            pkg.addComponent("models/7/9.onnx","application/octet-stream",bytes,Map.of("resourceType","MODEL"));
            byte[] archive=new DecisionArtifactPackageCodec().encode(pkg);
            String archiveHash=Sha256Digests.bytes(archive), ref="ts-file-v1:packages/"+archiveHash.substring(0,2)+"/snapshot--"+archiveHash+".zip";
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                db.queryForObject("SELECT COUNT(*) FROM rule_file_reference",Long.class);
                a.put(ref,archiveHash,archive,true);
                assertTrue(b.contains(ref));
                assertArrayEquals(archive,b.read(ref,archiveHash,archive.length));
            });
            assertEquals(payloadChunks,(int)db.queryForObject("""
                    SELECT COUNT(*) FROM rule_file_object_part p WHERE p.object_digest=? AND EXISTS
                    (SELECT 1 FROM rule_file_object_part z WHERE z.object_digest=? AND z.chunk_digest=p.chunk_digest)
                    """,Integer.class,hash,archiveHash));
            db.update("UPDATE rule_file_chunk SET content=X'00' WHERE digest=(SELECT chunk_digest FROM rule_file_object_part WHERE object_digest=? AND part_no=0)",hash);
            assertThrows(IllegalStateException.class,()->b.read(first,hash,bytes.length));
        } finally {control.execute("DROP DATABASE "+name);}
    }
}
