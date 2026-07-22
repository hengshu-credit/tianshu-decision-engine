package com.hengshucredit.rule.server.config;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ThirdPartyLicenseContractTest {

    @Test
    public void jpmmlAgplDeliveryNoticeIsVersionedAndDiscoverable() throws Exception {
        Path root = repositoryRoot();
        String pom = read(root.resolve("pom.xml"));
        String thirdParty = read(root.resolve("THIRD_PARTY_LICENSES.md"));
        String agpl = read(root.resolve("licenses/JPMML-EVALUATOR-AGPL-3.0.txt"));
        String notice = read(root.resolve("NOTICE"));

        Assert.assertTrue(pom.contains("<jpmml.version>1.7.7</jpmml.version>"));
        Assert.assertTrue(thirdParty.contains("JPMML Evaluator Metro 1.7.7"));
        Assert.assertTrue(thirdParty.contains("AGPL-3.0"));
        Assert.assertTrue(thirdParty.contains("https://github.com/jpmml/jpmml-evaluator"));
        Assert.assertTrue(thirdParty.contains("Corresponding Source"));
        Assert.assertTrue(thirdParty.contains("mvn clean package -DskipTests"));
        Assert.assertTrue(agpl.contains("GNU AFFERO GENERAL PUBLIC LICENSE"));
        Assert.assertTrue(notice.contains("THIRD_PARTY_LICENSES.md"));
    }

    private static Path repositoryRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(cwd.resolve("rule-engine-server")) ? cwd : cwd.getParent();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
