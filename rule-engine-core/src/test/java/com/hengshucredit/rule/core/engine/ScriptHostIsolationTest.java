package com.hengshucredit.rule.core.engine;

import org.junit.Test;
import java.util.Map;
import java.util.List;
import static org.junit.Assert.*;

public class ScriptHostIsolationTest {
    public static List<String> forbiddenScripts() {
        return List.of("System.getenv()", "System.getProperties()", "Runtime.getRuntime()",
                "new java.io.File('.')", "java.nio.file.Files.readString(java.nio.file.Path.of('.env.server'))",
                "new ProcessBuilder('whoami')", "Class.forName('java.lang.System')",
                "'test'.getClass().getClassLoader()", "'test'.class", "Thread.currentThread()",
                "new java.net.URL('file:.env.server').openStream()");
    }

    @Test public void scriptsCannotReachHostFilesProcessesReflectionOrEnvironment() {
        QLExpressEngine engine = new QLExpressEngine();
        for (String script : forbiddenScripts()) assertFalse(script, engine.execute(script, Map.of()).isSuccess());
        var result = engine.execute("' business '.trim().length() + 2", Map.of());
        assertTrue(result.getErrorMessage(), result.isSuccess()); assertEquals(10, result.getResult());
    }
}
