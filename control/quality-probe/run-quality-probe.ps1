# Read-only, reproducible probe for the two Task 06 candidate implementations.
# Output logs are saved only under the results worktree.
param(
    [string]$BenchRoot = 'C:\Workspace\github\java-spec-generator-bench',
    [string]$OutputPath = ''
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\dev\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;C:\dev\apache-maven-3.9.16\bin;$env:Path"
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$mvn = 'C:\dev\apache-maven-3.9.16\bin\mvn.cmd'
$probe = Join-Path $PSScriptRoot 'Task06QualityProbe.java'
$classpathFile = Join-Path $env:TEMP 'task06-independent-review-deps.txt'
if (-not (Test-Path $java) -or -not (Test-Path $mvn) -or -not (Test-Path $probe)) {
    throw 'Missing JDK, Maven, or quality probe.'
}
if (-not $OutputPath) {
    $OutputPath = Join-Path $PSScriptRoot '2026-11-06-compare-output.txt'
}
$buildFrom = Join-Path $BenchRoot 'dev-agentdock-retest'
Push-Location $buildFrom
try {
    & $mvn -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Maven dependency classpath resolution failed.' }
} finally {
    Pop-Location
}
$deps = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# Task 06 independent quality probe; source branches not modified')
$lines.Add('# Source-file execution; both runs use the exact same Probe.java and synthetic input.')
foreach ($name in @('dev-agentdock-retest','dev-agentdock-instant')) {
    $w = Join-Path $BenchRoot $name
    $head = (& git -C $w rev-parse HEAD).Trim()
    $cp = "$(Join-Path $w 'target\classes');$deps"
    $lines.Add("===== $name (HEAD $head) =====")
    $output = & $java -cp $cp $probe $w 2>&1
    $exit = $LASTEXITCODE
    foreach ($line in $output) { $lines.Add([string]$line) }
    $lines.Add("JAVA_EXIT=$exit")
    if ($exit -ne 0) { throw "$name probe failed with exit $exit" }
}
[System.IO.File]::WriteAllLines($OutputPath, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Written quality probe output: $OutputPath"
$lines
