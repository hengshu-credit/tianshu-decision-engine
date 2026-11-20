package com.hengshucredit.rule.server.example;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.jcraft.jsch.JSch;
import java.io.ByteArrayOutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.util.*;

final class ExampleCredentials {
    private static final SecureRandom RANDOM = new SecureRandom();

    static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static void privateFile(Path path, byte[] bytes) throws Exception {
        Path tmp = Files.createTempFile(path.getParent(), ".credit-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    static void provision() throws Exception {
        Path state = Path.of("/state"), supplier = Path.of("/supplier-state/supplier.json");
        Files.createDirectories(state);
        Files.createDirectories(supplier.getParent());
        try (var channel = FileChannel.open(state.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            if (!lock.isValid()) throw new IllegalStateException("无法锁定演示凭据卷");
            Path file = state.resolve("installation.json");
            if (!Files.exists(file)) {
                if (Files.exists(supplier)) throw new IllegalStateException("请恢复配套的安装凭据卷");
                privateFile(file, CanonicalJson.writeBytes(generate()));
            }
            var keys = CanonicalJson.readMap(Files.readAllBytes(file));
            var shared = new LinkedHashMap<>(keys);
            for (var name : List.of("db_user","db_password","credit_api","host_public")) shared.remove(name);
            if (Files.exists(supplier) && !shared.equals(CanonicalJson.readMap(Files.readAllBytes(supplier))))
                throw new IllegalStateException("供应商凭据卷与安装凭据不匹配");
            createReader(keys);
            if (!Files.exists(supplier)) privateFile(supplier, CanonicalJson.writeBytes(shared));
        }
    }

    private static Map<String,Object> generate() throws Exception {
        Map<String,Object> keys = new LinkedHashMap<>();
        for (String name : List.of("court","black","late","multi","income","credit","pboc","sftp","engine","db_password","credit_api")) keys.put(name,token());
        keys.put("db_user","hs_example_" + UUID.randomUUID().toString().replace("-", "").substring(0,16));
        var rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(3072);
        keys.put("fraud","-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(rsa.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        var host = com.jcraft.jsch.KeyPair.genKeyPair(new JSch(),com.jcraft.jsch.KeyPair.RSA,3072);
        try {
            var pem = new ByteArrayOutputStream();
            host.writePrivateKey(pem);
            keys.put("sftp_host_key",pem.toString(StandardCharsets.US_ASCII));
            keys.put("host_public","ssh-rsa " + Base64.getEncoder().encodeToString(host.getPublicKeyBlob()));
        } finally { host.dispose(); }
        return keys;
    }

    private static void createReader(Map<String,Object> keys) throws Exception {
        String user = (String)keys.get("db_user"), pass = (String)keys.get("db_password");
        if (!user.matches("hs_example_[0-9a-f]{16}") || !pass.matches("[A-Za-z0-9_-]{30,64}"))
            throw new IllegalArgumentException("只读账号凭据格式无效");
        String url = "jdbc:mysql://" + System.getenv("MYSQL_HOST") + ":" + Integer.parseInt(System.getenv("MYSQL_PORT")) + "/information_schema?allowPublicKeyRetrieval=true";
        try (var admin = DriverManager.getConnection(url,System.getenv("MYSQL_ADMIN_USER"),System.getenv("MYSQL_ADMIN_PASSWORD"));
             var sql = admin.createStatement()) {
            try (var roles = sql.executeQuery("SELECT @@mandatory_roles")) {
                roles.next();
                if (!Objects.toString(roles.getString(1),"").isBlank()) throw new IllegalStateException("请先核对 MySQL 强制角色");
            }
            try (var create = admin.prepareStatement("CREATE USER IF NOT EXISTS '" + user + "'@'%' IDENTIFIED BY ?")) {
                create.setString(1,pass);
                create.execute();
            }
            try (var grants = sql.executeQuery("SHOW GRANTS FOR '" + user + "'@'%'")) {
                if (!grants.next() || !grants.getString(1).startsWith("GRANT USAGE ON *.* TO ") || grants.next())
                    throw new IllegalStateException("演示只读账号存在额外权限");
            }
        }
        try (var reader = DriverManager.getConnection(url,user,pass); var s = reader.createStatement(); var rs = s.executeQuery("SELECT 1")) {
            if (!rs.next() || rs.getInt(1)!=1) throw new IllegalStateException("演示只读账号验证失败");
        }
    }
}
