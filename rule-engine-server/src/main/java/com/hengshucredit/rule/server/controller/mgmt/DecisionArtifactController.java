package com.hengshucredit.rule.server.controller.mgmt;

import com.hengshucredit.rule.model.dto.ArtifactDeployRequest;
import com.hengshucredit.rule.model.dto.ArtifactImportResult;
import com.hengshucredit.rule.model.entity.ArtifactDeployment;
import com.hengshucredit.rule.model.entity.ArtifactResourceBinding;
import com.hengshucredit.rule.model.entity.DecisionArtifact;
import com.hengshucredit.rule.model.entity.RulePublishOutbox;
import com.hengshucredit.rule.server.artifact.ArtifactDeploymentService;
import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import com.hengshucredit.rule.server.service.RulePublishOutboxService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rule/artifact")
public class DecisionArtifactController {
    @Resource
    private ArtifactDeploymentService deploymentService;
    @Resource
    private ConsoleOperatorResolver operatorResolver;
    @Resource
    private RulePublishOutboxService outboxService;

    @GetMapping("/{artifactId:\\d+}")
    public R<Map<String, Object>> detail(@PathVariable Long artifactId) {
        return R.ok(deploymentService.describeArtifact(artifactId));
    }

    @GetMapping("/{artifactId:\\d+}/deployments")
    public R<List<ArtifactDeployment>> deployments(@PathVariable Long artifactId) {
        return R.ok(deploymentService.listDeployments(artifactId));
    }

    @GetMapping("/deployments/{deploymentId:\\d+}/bindings")
    public R<List<ArtifactResourceBinding>> bindings(@PathVariable Long deploymentId) {
        return R.ok(deploymentService.listBindings(deploymentId));
    }

    @GetMapping("/outbox")
    public R<List<RulePublishOutbox>> outbox(@RequestParam Long definitionId,
                                            @RequestParam(defaultValue = "50") int limit) {
        return R.ok(outboxService.listRecent(definitionId, limit));
    }

    @GetMapping("/{artifactId:\\d+}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long artifactId) {
        DecisionArtifact artifact = deploymentService.getArtifact(artifactId);
        if (artifact == null || artifact.getPackageContent() == null) {
            throw new IllegalArgumentException("决策制品不存在");
        }
        String fileName = "artifact-" + artifactId + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("X-Artifact-Digest", artifact.getArtifactDigest())
                .header("X-Package-Digest", artifact.getPackageDigest())
                .contentLength(artifact.getPackageContent().length)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(artifact.getPackageContent());
    }

    @PostMapping("/import")
    public R<ArtifactImportResult> importArtifact(@RequestPart("file") MultipartFile file,
                                                  @RequestParam(required = false)
                                                  String expectedPackageDigest) {
        try {
            if (file == null || file.isEmpty()) return R.fail(422, "制品文件不能为空");
            return R.ok(deploymentService.importArtifact(file.getBytes(), expectedPackageDigest, actor()));
        } catch (IllegalArgumentException e) {
            return R.fail(422, e.getMessage());
        } catch (Exception e) {
            return R.fail(500, "读取制品文件失败: " + e.getMessage());
        }
    }

    @PostMapping("/deploy")
    public R<ArtifactDeployment> deploy(@RequestBody ArtifactDeployRequest request) {
        try {
            return R.ok(deploymentService.deploy(request, actor()));
        } catch (IllegalArgumentException e) {
            return R.fail(422, e.getMessage());
        } catch (IllegalStateException e) {
            return R.fail(409, e.getMessage());
        }
    }

    private String actor() {
        return operatorResolver == null ? ConsoleOperatorResolver.SYSTEM_CONSOLE : operatorResolver.resolve();
    }
}
