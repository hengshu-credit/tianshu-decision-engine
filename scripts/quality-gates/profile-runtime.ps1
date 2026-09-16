param([string]$Label = 'current')

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $repoRoot
try {
    $outputDir = Join-Path $repoRoot '.codex-validation/runtime-cache'
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    & mvn -q -pl rule-engine-core -am test-compile -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
    & mvn -q -pl rule-engine-core dependency:build-classpath '-Dmdep.outputFile=target/profile-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Classpath generation failed' }
    $profileClasspath = 'rule-engine-core/target/classes;rule-engine-model/target/classes;' +
        (Get-Content rule-engine-core/target/profile-classpath.txt -Raw).Trim()
    & javac -encoding UTF-8 -cp $profileClasspath -d $outputDir `
        scripts/quality-gates/RuntimeContextProfile.java scripts/quality-gates/SummarizeRuntimeJfr.java
    if ($LASTEXITCODE -ne 0) { throw 'Profiler compilation failed' }
    $prefix = Join-Path $outputDir $Label
    & java -Xms256m -Xmx256m -cp "$outputDir;$profileClasspath" RuntimeContextProfile $prefix |
        Tee-Object -FilePath "$prefix.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Runtime profiling failed' }
    $recordings = Get-ChildItem -LiteralPath $outputDir -Filter "$Label-*.jfr" | Select-Object -ExpandProperty FullName
    & java -cp $outputDir SummarizeRuntimeJfr @recordings | Tee-Object -FilePath "$prefix-summary.txt"
    if ($LASTEXITCODE -ne 0) { throw 'JFR summary failed' }
} finally {
    Pop-Location
}
