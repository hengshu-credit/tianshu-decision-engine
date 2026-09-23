param([switch]$Rebuilt, [Parameter(ValueFromRemainingArguments=$true)][string[]]$ApplicationArgs)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    if ($Rebuilt) {
        if (-not (Test-Path -LiteralPath 'build/example.jar')) { throw 'Run java tools/BuildExample.java first.' }
        & java -cp 'build/example.jar;example/lib/*' com.hengshucredit.rule.example.RuleExampleApplication @ApplicationArgs
    } else {
        & java -jar 'example/rule-engine-example.jar' @ApplicationArgs
    }
    exit $LASTEXITCODE
} finally { Pop-Location }
