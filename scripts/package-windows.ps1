[CmdletBinding()]
param(
    [string]$JdkHome = 'C:\dev\jdk-21',
    [string]$MavenCmd = 'C:\dev\apache-maven-3.9.16\bin\mvn.cmd',
    [switch]$SkipTests,
    [switch]$SkipJpackage
)

$ErrorActionPreference = 'Stop'

function Require-Path {
    param([string]$Path, [string]$Label)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label was not found: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

$root = Split-Path -Parent $PSScriptRoot
$pom = Join-Path $root 'pom.xml'
$jdk = Require-Path $JdkHome 'JDK home'
$mvn = Require-Path $MavenCmd 'Maven command'
$java = Require-Path (Join-Path $jdk 'bin\java.exe') 'java.exe'
$jlink = Join-Path $jdk 'bin\jlink.exe'
$jpackage = Join-Path $jdk 'bin\jpackage.exe'

$env:JAVA_HOME = $jdk
$env:PATH = "$(Join-Path $jdk 'bin');$(Split-Path -Parent $mvn);$env:PATH"

Write-Host "Java: $(& $java -version 2>&1 | Select-Object -First 1)"
Write-Host "Maven: $mvn"

$mavenArgs = @('-q', '-f', $pom)
if ($SkipTests) {
    $mavenArgs += '-DskipTests'
} else {
    $mavenArgs += 'clean'
}
$mavenArgs += 'package'
& $mvn @mavenArgs
if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed with exit code $LASTEXITCODE"
}

$jar = Join-Path $root 'target\spec-generator.jar'
$jar = Require-Path $jar 'Shaded executable JAR'
Write-Host "Executable JAR: $jar"

if ($SkipJpackage) {
    return
}

$target = Join-Path $root 'target'
$runtime = Join-Path $target 'jlink-runtime'
$staging = Join-Path $target 'jpackage-input'
$appImageRoot = Join-Path $target 'jpackage'
$work = Join-Path $target 'jpackage-work'
foreach ($path in @($runtime, $staging, $appImageRoot, $work)) {
    if (Test-Path -LiteralPath $path) {
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}
New-Item -ItemType Directory -Path $staging, $work -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $staging 'spec-generator.jar')

Write-Host 'Creating the self-contained JDK runtime with jlink...'
$jlinkArgs = @(
    '--module-path', (Join-Path $jdk 'jmods'),
    '--add-modules', 'ALL-MODULE-PATH',
    '--output', $runtime,
    '--strip-debug', '--no-header-files', '--no-man-pages',
    '--compress=zip-6'
)
& $jlink @jlinkArgs
if ($LASTEXITCODE -ne 0) {
    throw "jlink failed with exit code $LASTEXITCODE"
}

$guiProperties = Join-Path $work 'gui-launcher.properties'
Set-Content -LiteralPath $guiProperties -Encoding ascii -Value @(
    'app-version=1.0.0',
    'main-jar=spec-generator.jar',
    'main-class=com.comus93.specgen.SpecGeneratorApp',
    'arguments=gui',
    'win-console=false',
    'java-options=-Dfile.encoding=UTF-8'
)

Write-Host 'Creating the Windows app-image with jpackage...'
$jpackageArgs = @(
    '--type', 'app-image',
    '--name', 'SpecGenerator',
    '--input', $staging,
    '--main-jar', 'spec-generator.jar',
    '--main-class', 'com.comus93.specgen.SpecGeneratorApp',
    '--runtime-image', $runtime,
    '--dest', $appImageRoot,
    '--win-console',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--add-launcher', "SpecGeneratorGUI=$guiProperties"
)
& $jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

$cli = Join-Path $appImageRoot 'SpecGenerator\SpecGenerator.exe'
$gui = Join-Path $appImageRoot 'SpecGenerator\SpecGeneratorGUI.exe'
if (-not (Test-Path -LiteralPath $cli)) {
    throw "CLI launcher was not created: $cli"
}
if (-not (Test-Path -LiteralPath $gui)) {
    throw "GUI launcher was not created: $gui"
}

Write-Host "CLI launcher: $cli"
Write-Host "GUI launcher: $gui"
Write-Host "App image: $(Join-Path $appImageRoot 'SpecGenerator')"
