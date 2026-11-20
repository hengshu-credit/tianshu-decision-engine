package com.hengshucredit.rule.server.example;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.Collectors;

final class ExampleSupplier {
    private static final List<String> INPUTS = List.of("name","idcard_no","mobile_no","request_id");
    private static final Map<String,String> RULES = Map.of("court","COURT","black","BLACK","late","OVERDUE","multi","MULTI","income","INCOME","fraud","FRAUD","credit","CREDIT","pboc","PBOC");
    private static final Map<String,String> FLAGS = Map.of("court","matched","black","isHitData","late","located","multi","available","income","found","fraud","matched","credit","available","pboc","reportFound");
    private static final Pattern DIGEST_MEMBER = Pattern.compile("(\\w+)=(?:\"([^\"\\r\\n]*)\"|([^,\\s]+))");
    private static final Path REPORTS = Path.of("/srv/credit-example/reports");
    private final Map<String,Object> keys;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String engine = System.getenv("EXAMPLE_ENGINE_URL").replaceAll("/+$","");
    private final PublicKey fraudKey;
    private final ConcurrentMap<String,Long> tokens = new ConcurrentHashMap<>();
    private final Map<String,Job> jobs = new HashMap<>();
    private record Job(long ready, long expiry, boolean billable, boolean found, byte[] report) {}

    ExampleSupplier() throws Exception {
        keys = CanonicalJson.readMap(Files.readAllBytes(Path.of("/state/supplier.json")));
        var factory = KeyFactory.getInstance("RSA");
        var der = Base64.getMimeDecoder().decode(key("fraud").replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----",""));
        var privateKey = (RSAPrivateCrtKey)factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        fraudKey = factory.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(),privateKey.getPublicExponent()));
    }

    void run() throws Exception {
        Files.createDirectories(REPORTS);
        Files.createDirectories(Path.of("/run/sshd"));
        Files.createDirectories(Path.of("/run/credit-example"));
        ExampleCredentials.privateFile(Path.of("/run/credit-example/host.key"),bytes(key("sftp_host_key")));
        if (!key("sftp").matches("[A-Za-z0-9_-]{30,64}")) throw new IllegalArgumentException("SFTP 口令格式无效");
        var password = new ProcessBuilder("chpasswd").start();
        try (var stdin = password.getOutputStream()) { stdin.write(bytes("pboc_sftp:" + key("sftp") + "\n")); }
        if (password.waitFor()!=0) throw new IOException("无法创建 SFTP 只读账号");
        // Only the last published report files are exposed within the OpenSSH chroot.
        try (var stale = Files.newDirectoryStream(REPORTS,"*.json")) {
            for (Path path : stale) Files.delete(path);
        }
        var http = HttpServer.create(new InetSocketAddress(28084),64);
        http.createContext("/",this::handle);
        http.setExecutor(Executors.newFixedThreadPool(32));
        var ssh = new ProcessBuilder("/usr/sbin/sshd","-D","-e","-f","/etc/ssh/sshd_config_example").inheritIO().start();
        Runtime.getRuntime().addShutdownHook(new Thread(ssh::destroy));
        var cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleWithFixedDelay(this::expire,1,1,TimeUnit.MINUTES);
        http.start();
        ssh.waitFor();
        http.stop(0);
        throw new IOException("SFTP 服务已退出");
    }

    private synchronized void expire() {
        long now = System.currentTimeMillis();
        tokens.values().removeIf(expiry -> expiry <= now);
        jobs.entrySet().removeIf(entry -> {
            if (entry.getValue().expiry() > now) return false;
            try { Files.deleteIfExists(REPORTS.resolve(entry.getKey()+".json")); return true; }
            catch (IOException error) { return false; }
        });
    }
    private String key(String name) { return (String)keys.get(name); }
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static boolean eq(String a,String b) { return a!=null && b!=null && MessageDigest.isEqual(bytes(a),bytes(b)); }
    private static String basic(String user,String pass) { return "Basic "+Base64.getEncoder().encodeToString(bytes(user+":"+pass)); }
    private String token(String prefix,long seconds) {
        String value = ExampleCredentials.token();
        tokens.put(prefix+value,System.currentTimeMillis()+1000*seconds);
        return value;
    }
    private boolean valid(String prefix,String value) { return tokens.getOrDefault(prefix+value,0L)>System.currentTimeMillis(); }
    private boolean once(String value) { return tokens.putIfAbsent("used:"+value,System.currentTimeMillis()+180000)==null; }
    private static String sha(String text) throws GeneralSecurityException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(text))); }
    private byte[] mac(String source,String text) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(bytes(key(source)),"HmacSHA256"));
        return mac.doFinal(bytes(text));
    }

    private boolean authorized(String source,HttpExchange x,Map<String,Object> body) throws GeneralSecurityException {
        var headers=x.getRequestHeaders();
        String auth=Objects.toString(headers.getFirst("Authorization"),"");
        String path=x.getRequestURI().getRawPath();
        return switch(source) {
            case "court" -> eq(auth,basic("court_client",key("court")));
            case "black" -> eq(auth,"Bearer "+key("black"));
            case "late" -> eq(headers.getFirst("X-Supplier-Key"),key("late"));
            case "income" -> auth.startsWith("Bearer ") && valid("oauth:",auth.substring(7));
            case "credit" -> Arrays.stream(Objects.toString(headers.getFirst("Cookie"),"").split(";"))
                    .map(String::trim).anyMatch(c -> c.startsWith("hscredit_session=") && valid("cookie:",c.substring("hscredit_session=".length())));
            case "multi" -> {
                String ts=Objects.toString(headers.getFirst("X-Request-Time"),""), nonce=Objects.toString(headers.getFirst("X-Request-Nonce"),"");
                if (!ts.matches("[0-9]{13}") || Math.abs(System.currentTimeMillis()-Long.parseLong(ts))>90000 || !nonce.matches("[A-Za-z0-9_-]{16,80}")) yield false;
                String signed="POST\n"+path+"\n"+ts+"\n"+nonce+"\n"+INPUTS.stream().map(k->Objects.toString(body.get(k),"")).collect(Collectors.joining("\n"));
                yield eq(headers.getFirst("X-Request-Signature"),Base64.getEncoder().encodeToString(mac("multi",signed))) && once("multi:"+nonce);
            }
            case "fraud" -> verifyJwt(auth,body.get("request_id"));
            case "pboc" -> verifyDigest(auth,path);
            default -> false;
        };
    }

    private boolean verifyJwt(String auth,Object req) {
        if (!auth.startsWith("JWT ")) return false;
        try {
            String[] p=auth.substring(4).split("\\.",-1);
            if (p.length!=3 || !"RS256".equals(CanonicalJson.readMap(Base64.getUrlDecoder().decode(p[0])).get("alg"))) return false;
            Signature verifier=Signature.getInstance("SHA256withRSA");
            verifier.initVerify(fraudKey);verifier.update(bytes(p[0]+"."+p[1]));
            if (!verifier.verify(Base64.getUrlDecoder().decode(p[2]))) return false;
            var c=CanonicalJson.readMap(Base64.getUrlDecoder().decode(p[1]));
            long now=System.currentTimeMillis()/1000, iat=((Number)c.get("iat")).longValue(),exp=((Number)c.get("exp")).longValue();
            return now>=iat && now<exp && exp-iat<=120 && "demo-fraud-client".equals(c.get("iss")) && "demo-fraud".equals(c.get("aud"))
                    && req!=null && req.equals(c.get("req")) && c.get("jti") instanceof String id && !id.isBlank() && once("jwt:"+id);
        } catch (RuntimeException | GeneralSecurityException invalid) { return false; }
    }
    private boolean verifyDigest(String auth,String path) throws GeneralSecurityException {
        if (!auth.startsWith("Digest ")) return false;
        Map<String,String> d=new HashMap<>();Matcher m=DIGEST_MEMBER.matcher(auth.substring(7));
        while(m.find()) d.put(m.group(1),m.group(2)==null?m.group(3):m.group(2));
        String nonce=d.get("nonce"), nc=d.getOrDefault("nc",""), cn=d.getOrDefault("cnonce","");
        if (!"pboc_client".equals(d.get("username")) || !"demo-pboc".equals(d.get("realm")) || !"SHA-256".equalsIgnoreCase(d.get("algorithm"))
                || !path.equals(d.get("uri")) || !"auth".equals(d.get("qop")) || !valid("digest:",nonce) || !nc.matches("[a-fA-F0-9]{8}") || cn.isEmpty()) return false;
        String expected=sha(String.join(":",sha("pboc_client:demo-pboc:"+key("pboc")),nonce,nc,cn,"auth",sha("POST:"+path)));
        return eq(expected,d.get("response")) && once("digest:"+nonce+":"+cn+":"+nc);
    }
    private Map<String,Object> challenge(HttpExchange x) {
        String nonce=token("digest:",180);
        x.getResponseHeaders().set("WWW-Authenticate","Digest realm=\"demo-pboc\", nonce=\""+nonce+"\", algorithm=SHA-256, qop=\"auth\"");
        return Map.of("realm","demo-pboc","nonce",nonce,"algorithm","SHA-256","qop","auth");
    }
    private void send(HttpExchange x,int code,Object obj) throws IOException {
        byte[] payload=CanonicalJson.writeBytes(obj);
        x.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.sendResponseHeaders(code,payload.length);
        x.getResponseBody().write(payload);
    }
    private void handle(HttpExchange x) throws IOException {
        try {
            String path=x.getRequestURI().getPath();
            if ("/pboc/challenge".equals(path) && Set.of("GET","POST").contains(x.getRequestMethod())) { send(x,200,challenge(x));return; }
            if (!"POST".equals(x.getRequestMethod())) { send(x,405,Map.of("error","method"));return; }
            byte[] raw=x.getRequestBody().readNBytes(65537);
            if (raw.length>65536) {send(x,413,Map.of("error","payload"));return;}
            Map<String,Object> b;
            try {
                String contentType=Objects.toString(x.getRequestHeaders().getFirst("Content-Type"),"");
                if (contentType.startsWith("application/x-www-form-urlencoded") && (path.equals("/oauth/token") || path.equals("/credit/login"))) {
                    b=new LinkedHashMap<>();
                    for (String pair : new String(raw,StandardCharsets.UTF_8).split("&")) {
                        String[] field=pair.split("=",2);
                        if (field.length==2) b.put(URLDecoder.decode(field[0],StandardCharsets.UTF_8),URLDecoder.decode(field[1],StandardCharsets.UTF_8));
                    }
                } else b=CanonicalJson.readMap(raw);
            } catch(IllegalArgumentException e) {send(x,400,Map.of("error","json"));return;}
            if (path.equals("/oauth/token")) {
                if (!"client_credentials".equals(b.get("grant_type")) || !"income_client".equals(b.get("client_id")) || !eq(Objects.toString(b.get("client_secret"),""),key("income"))) {send(x,401,Map.of("error","invalid_client"));return;}
                send(x,200,Map.of("access_token",token("oauth:",300),"expires_in",300,"token_type","Bearer"));return;
            }
            if (path.equals("/credit/login")) {
                if (!"credit_client".equals(b.get("username")) || !eq(Objects.toString(b.get("password"),""),key("credit"))) {send(x,401,Map.of("error","invalid_login"));return;}
                String cookie="hscredit_session="+token("cookie:",300);
                x.getResponseHeaders().set("Set-Cookie",cookie+"; HttpOnly; SameSite=Strict; Max-Age=300; Path=/data/credit");
                send(x,200,Map.of("cookie",cookie,"expires_in",300));return;
            }
            boolean poll=path.equals("/pboc/status");
            String source=poll || path.equals("/pboc/apply") ? "pboc" : path.startsWith("/data/") ? path.substring(6) : "";
            if (!RULES.containsKey(source) || path.equals("/data/pboc")) {send(x,404,Map.of("error","path"));return;}
            if (!authorized(source,x,b)) {if (source.equals("pboc")) challenge(x);send(x,401,Map.of("error","invalid_credentials"));return;}
            if (poll) {status(x,Objects.toString(b.get("serial"),""));return;}
            if (!INPUTS.stream().allMatch(k->b.get(k) instanceof String v && !v.isBlank() && v.length()<=128)) {send(x,400,Map.of("error","missing_input"));return;}
            if (source.equals("pboc")) send(x,200,apply(b)); else send(x,200,query(source,b));
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            if (x.getResponseCode()==-1) send(x,503,Map.of("code","SUPPLIER_UNAVAILABLE"));
        } finally {x.close();}
    }

    private Map<String,Object> query(String source, Map<String,Object> b) throws Exception {
        var params=new LinkedHashMap<String,Object>();
        INPUTS.forEach(k->params.put(k,b.get(k)));
        var request=HttpRequest.newBuilder(URI.create(engine+"/api/rule/sync/execute/MOCK_"+RULES.get(source)))
                .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("Authorization",basic("demo_supplier_gateway",key("engine")))
                .POST(HttpRequest.BodyPublishers.ofByteArray(CanonicalJson.writeBytes(Map.of("params",params,"clientAppName","本地模拟供应商","traceEnabled",false)))).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
        var outer=CanonicalJson.readMap(response.body());
        if (response.statusCode()!=200 || !Boolean.TRUE.equals(outer.get("success")) || !(outer.get("data") instanceof Map<?,?> data) || !Boolean.TRUE.equals(data.get("success"))
                || !(data.get("result") instanceof Map<?,?> out) || !(out.get("supplier_payload") instanceof Map<?,?> payload) || !(payload.get(FLAGS.get(source)) instanceof Boolean))
            throw new IOException("模拟供应商规则缺少有效输出");
        return CanonicalJson.readMap(CanonicalJson.write(payload));
    }
    private synchronized Map<String,Object> apply(Map<String,Object> b) throws Exception {
        String identity=List.of("request_id","name","idcard_no","mobile_no").stream().map(k->CanonicalJson.write(b.get(k))).collect(Collectors.joining(", ","[","]"));
        String serial=HexFormat.of().formatHex(mac("pboc",identity)).substring(0,32);
        long now=System.currentTimeMillis();Job job=jobs.get(serial);
        if (job==null || now>=job.expiry()) {
            var p=query("pboc",b);
            if (!(p.get("billable") instanceof Boolean billable)) throw new IOException("缺少受理计费标志");
            boolean found=(Boolean)p.get("reportFound");
            var report=new LinkedHashMap<String,Object>(); report.put("serial",serial);report.put("reportFound",found);report.put("report",p.get("report"));
            job=new Job(now+(b.get("request_id").toString().startsWith("DEMO-PBOC-RESUME-")?15000:2500),now+3600000,billable,found,
                    Boolean.TRUE.equals(p.get("deliveryFailed"))?null:CanonicalJson.writeBytes(report));
            jobs.put(serial,job);
        }
        return Map.of("code","0000","serial",serial,"billable",job.billable());
    }
    private synchronized void status(HttpExchange x,String serial) throws IOException {
        Job job=jobs.get(serial);long now=System.currentTimeMillis();
        if (job==null || now>=job.expiry()) {send(x,404,Map.of("code","UNKNOWN_SERIAL"));return;}
        if (now<job.ready()) {send(x,200,Map.of("code","PENDING","serial",serial));return;}
        if (job.report()==null) {send(x,200,Map.of("code","REPORT_FAILED","serial",serial));return;}
        Path file=REPORTS.resolve(serial+".json");
        if (!Files.exists(file)) {
            // Stage outside the readable directory so SFTP can never observe a partial report.
            Path tmp = Files.createTempFile(REPORTS.getParent().getParent(), ".report-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(tmp, job.report());
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("r--r--r--"));
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(tmp); }
        }
        send(x,200,Map.of("code","READY","serial",serial,"reportFound",job.found(),"report_path","/reports/"+serial+".json"));
    }
}
