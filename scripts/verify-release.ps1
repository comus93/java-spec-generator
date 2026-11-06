[CmdletBinding()]
param(
    [string]$JdkHome = 'C:\dev\jdk-21',
    [string]$MavenCmd = 'C:\dev\apache-maven-3.9.16\bin\mvn.cmd',
    [string]$Java17Home = $env:JAVA17_HOME,
    [switch]$SkipPackage
)

$ErrorActionPreference = 'Stop'

function Require-Path {
    param([string]$Path, [string]$Label)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label was not found: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).Path
}

function Add-Result {
    param([string]$Check, [string]$Status, [string]$Detail, [string]$Category = 'Functional')
    $script:results += [pscustomobject]@{ Category = $Category; Check = $Check; Status = $Status; Detail = $Detail }
    Write-Host ("[{0}] {1}: {2}" -f $Status, $Check, $Detail)
}

function Invoke-MavenLogged {
    param([string]$Log, [string[]]$Arguments)
    $output = & $script:mvn '-f' $script:pom @Arguments 2>&1
    $code = $LASTEXITCODE
    New-Item -ItemType Directory -Path (Split-Path -Parent $Log) -Force | Out-Null
    $output | Set-Content -LiteralPath $Log -Encoding utf8
    return [pscustomobject]@{ Code = $code; Output = ($output -join [Environment]::NewLine) }
}

function Get-JarClassMajor {
    param([string]$Jar)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $maxMajor = 0
        $unsupported = New-Object System.Collections.Generic.List[string]
        foreach ($entry in $zip.Entries) {
            if (-not $entry.FullName.EndsWith('.class', [StringComparison]::OrdinalIgnoreCase)) {
                continue
            }
            if ($entry.FullName.StartsWith('META-INF/versions/', [StringComparison]::OrdinalIgnoreCase)) {
                continue
            }
            $stream = $entry.Open()
            try {
                $buffer = New-Object byte[] 8
                $read = $stream.Read($buffer, 0, 8)
            } finally {
                $stream.Dispose()
            }
            if ($read -lt 8) {
                continue
            }
            $major = (($buffer[6] -as [int]) -shl 8) -bor ($buffer[7] -as [int])
            if ($major -gt $maxMajor) {
                $maxMajor = $major
            }
            if ($major -gt 61) {
                $unsupported.Add("$($entry.FullName) (major $major)")
            }
        }
        return [pscustomobject]@{ MaxMajor = $maxMajor; Unsupported = $unsupported }
    } finally {
        $zip.Dispose()
    }
}

function New-ReleaseJobConfig {
    param([string]$Mode, [string]$OutputDirectory, [string]$SourceFolder, [string]$ModuleName)
    $job = [ordered]@{
        mode = $Mode
        system = 'SYS1'
        author = 'release-validation'
        authoringDateOverride = '2019-10-28'
        programName = 'Program Spec Generator'
        overview = 'Offline release validation'
        outputDirectory = $OutputDirectory
        outputFormat = 'xlsx'
        modules = @(
            [ordered]@{
                sourceFolder = $SourceFolder
                subsystem = 'SYS'
                moduleName = $ModuleName
            }
        )
    }
    New-Item -ItemType Directory -Path $script:work -Force | Out-Null
    $path = Join-Path $script:work "job-$Mode.json"
    $json = $job | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($path, $json, [System.Text.UTF8Encoding]::new($false))
    return $path
}

function Count-Workbooks {
    param([string]$Directory)
    if (-not (Test-Path -LiteralPath $Directory)) {
        return 0
    }
    return @(Get-ChildItem -LiteralPath $Directory -Recurse -File |
        Where-Object { $_.Extension -in @('.xls', '.xlsx') }).Count
}

$root = Split-Path -Parent $PSScriptRoot
$script:pom = Join-Path $root 'pom.xml'
$script:results = @()
$script:jdk = Require-Path $JdkHome 'JDK home'
$script:mvn = Require-Path $MavenCmd 'Maven command'
$java = Require-Path (Join-Path $script:jdk 'bin\java.exe') 'java.exe'
$env:JAVA_HOME = $script:jdk
$env:PATH = "$(Join-Path $script:jdk 'bin');$(Split-Path -Parent $script:mvn);$env:PATH"

$releaseDir = Join-Path $root 'target\release'
$script:work = Join-Path $releaseDir 'work'
if (Test-Path -LiteralPath $releaseDir) {
    Remove-Item -LiteralPath $releaseDir -Recurse -Force
}
New-Item -ItemType Directory -Path $script:work -Force | Out-Null

$javaVersion = (& $java -version 2>&1 | Out-String).Trim()
Write-Host "Java runtime for this verification:"
Write-Host $javaVersion
Write-Host "Maven: $($script:mvn)"

$manifest = Join-Path $root 'docs/test/golden-manifest.sha256'
$assetStatus = 'PASS'
$assetDetails = New-Object System.Collections.Generic.List[string]
if (-not (Test-Path -LiteralPath $manifest)) {
    $assetStatus = 'FAIL'
    $assetDetails.Add('Golden manifest is missing.')
} else {
    foreach ($line in Get-Content -LiteralPath $manifest -Encoding utf8) {
        $clean = $line.Trim()
        if ($clean -eq '' -or $clean.StartsWith('#')) { continue }
        $parts = $clean -split '\s+', 2
        if ($parts.Count -ne 2) { continue }
        $expected = $parts[0].ToLowerInvariant()
        $relative = $parts[1].Trim()
        $asset = Join-Path $root $relative
        if (-not (Test-Path -LiteralPath $asset)) {
            $assetStatus = 'FAIL'
            $assetDetails.Add("MISSING $relative")
            continue
        }
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $asset).Hash.ToLowerInvariant()
        if ($actual -ne $expected) {
            $assetStatus = 'FAIL'
            $assetDetails.Add("MISMATCH $relative")
        } else {
            $assetDetails.Add("OK $relative")
        }
    }
}
Add-Result 'Golden reference asset integrity (informational only)' $assetStatus ($assetDetails -join '; ') -Category 'Asset'

$verify = Invoke-MavenLogged -Log (Join-Path $releaseDir 'maven-verify.log') -Arguments @('-q', 'clean', 'verify')
if ($verify.Code -eq 0) {
    Add-Result 'mvn clean verify' 'PASS' 'Unit, integration and release-contract tests completed.'
} else {
    Add-Result 'mvn clean verify' 'FAIL' "Maven exited with $($verify.Code); see target/release/maven-verify.log."
}

$jar = Join-Path $root 'target\spec-generator.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    $fallback = Invoke-MavenLogged -Log (Join-Path $releaseDir 'maven-package-fallback.log') `
        -Arguments @('-q', '-DskipTests', 'package')
    if ($fallback.Code -eq 0) {
        Add-Result 'Artifact build after failed test gate' 'PASS' 'Packaging still succeeded; test gate remains FAIL.' -Category 'Deployment'
    } else {
        Add-Result 'Artifact build after failed test gate' 'FAIL' 'Packaging failed; see target/release/maven-package-fallback.log.' -Category 'Deployment'
    }
}

if (Test-Path -LiteralPath $jar) {
    Add-Result 'Executable JAR' 'PASS' $jar -Category 'Deployment'
    $classInfo = Get-JarClassMajor -Jar $jar
    if ($classInfo.MaxMajor -le 61 -and $classInfo.Unsupported.Count -eq 0) {
        Add-Result 'Shaded JAR Java 17 class files' 'PASS' "Maximum base class-file major version is $($classInfo.MaxMajor) (Java 17 = 61)." -Category 'Deployment'
    } else {
        Add-Result 'Shaded JAR Java 17 class files' 'FAIL' ("Unsupported base entries: " + ($classInfo.Unsupported -join '; ')) -Category 'Deployment'
    }
} else {
    Add-Result 'Executable JAR' 'FAIL' "Missing: $jar" -Category 'Deployment'
}

$tree = Invoke-MavenLogged -Log (Join-Path $releaseDir 'maven-dependency-tree.log') -Arguments @('-q', 'dependency:tree', "-DoutputFile=$releaseDir\dependency-tree.txt")
if ($tree.Code -eq 0) {
    Add-Result 'Dependency inventory input' 'PASS' (Join-Path $releaseDir 'dependency-tree.txt') -Category 'Deployment'
} else {
    Add-Result 'Dependency inventory input' 'FAIL' 'mvn dependency:tree failed; see target/release/maven-dependency-tree.log.' -Category 'Deployment'
}

$fixtures = Join-Path $root 'src\test\resources\fixtures\spring-mvc'
$javaOutput = Join-Path $releaseDir 'cli-java'
$jspOutput = Join-Path $releaseDir 'cli-jsp'
$javaJob = New-ReleaseJobConfig -Mode 'JAVA' -OutputDirectory $javaOutput -SourceFolder $fixtures -ModuleName 'todo'
$jspJob = New-ReleaseJobConfig -Mode 'JSP' -OutputDirectory $jspOutput -SourceFolder $fixtures -ModuleName 'views'

if (Test-Path -LiteralPath $jar) {
    & $java -jar $jar generate --config $javaJob *>&1 | Tee-Object -FilePath (Join-Path $releaseDir 'cli-java.log') | Out-Host
    $javaExit = $LASTEXITCODE
    & $java -jar $jar generate --config $jspJob *>&1 | Tee-Object -FilePath (Join-Path $releaseDir 'cli-jsp.log') | Out-Host
    $jspExit = $LASTEXITCODE
    $javaCount = Count-Workbooks -Directory $javaOutput
    $jspCount = Count-Workbooks -Directory $jspOutput
    if ($javaExit -eq 0 -and $jspExit -eq 0 -and $javaCount -eq 2 -and $jspCount -eq 2) {
        Add-Result 'CLI generation on JDK 21' 'PASS' "Java=$javaCount workbook(s), JSP=$jspCount workbook(s), exit codes 0/0."
    } else {
        Add-Result 'CLI generation on JDK 21' 'FAIL' "exit codes $javaExit/$jspExit, workbooks $javaCount/$jspCount."
    }

    & $java -jar $jar generate --config $javaJob *>&1 | Out-Null
    $repeatExit = $LASTEXITCODE
    $temporaryFiles = @(Get-ChildItem -LiteralPath $javaOutput -Recurse -File -Filter '.specgen-*' -ErrorAction SilentlyContinue)
    if ($repeatExit -eq 0 -and $temporaryFiles.Count -eq 0 -and (Count-Workbooks -Directory $javaOutput) -eq 2) {
        Add-Result 'Repeated overwrite' 'PASS' 'Second run succeeded, retained two workbooks and left no temporary files.'
    } else {
        Add-Result 'Repeated overwrite' 'FAIL' "exit=$repeatExit, temporary files=$($temporaryFiles.Count)."
    }
} else {
    Add-Result 'CLI generation on JDK 21' 'FAIL' 'Executable JAR is unavailable.'
    Add-Result 'Repeated overwrite' 'FAIL' 'Executable JAR is unavailable.'
}

$java17 = $null
if ($Java17Home) {
    $candidate = Join-Path $Java17Home 'bin\java.exe'
    if (Test-Path -LiteralPath $candidate) {
        $java17 = (Resolve-Path -LiteralPath $candidate).Path
    }
} else {
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath -and $onPath.Source -notlike "*jdk-21*") {
        $candidate = $onPath.Source
        if (Test-Path -LiteralPath $candidate) {
            $java17 = (Resolve-Path -LiteralPath $candidate).Path
        }
    }
}
if ($java17) {
    $version17 = (& $java17 -version 2>&1 | Out-String).Trim()
    Write-Host "Configured Java 17 runtime:"
    Write-Host $version17
    if ($version17 -match 'version "17\.') {
        $java17Output = Join-Path $releaseDir 'cli-java17'
        $java17Job = New-ReleaseJobConfig -Mode 'JAVA' -OutputDirectory $java17Output -SourceFolder $fixtures -ModuleName 'todo'
        & $java17 -jar $jar generate --config $java17Job *>&1 | Tee-Object -FilePath (Join-Path $releaseDir 'cli-java17.log') | Out-Host
        $java17Exit = $LASTEXITCODE
        $java17Count = Count-Workbooks -Directory $java17Output
        if ($java17Exit -eq 0 -and $java17Count -eq 2) {
            Add-Result 'Actual Java 17 JVM CLI' 'PASS' "java -version reports 17; $java17Count workbooks generated." -Category 'Deployment'
        } else {
            Add-Result 'Actual Java 17 JVM CLI' 'FAIL' "exit=$java17Exit, workbooks=$java17Count." -Category 'Deployment'
        }
    } else {
        Add-Result 'Actual Java 17 JVM CLI' 'BLOCKED' 'Configured Java runtime is not version 17.' -Category 'Deployment'
    }
} else {
    Add-Result 'Actual Java 17 JVM CLI' 'BLOCKED' 'No Java 17 JVM is installed or configured (JAVA17_HOME is unset; PATH has no Java).' -Category 'Deployment'
}

if (-not $SkipPackage) {
    try {
        $packageScript = Join-Path $PSScriptRoot 'package-windows.ps1'
        & $packageScript -JdkHome $script:jdk -MavenCmd $script:mvn -SkipTests *>&1 |
            Tee-Object -FilePath (Join-Path $releaseDir 'package-windows.log') | Out-Host
        Add-Result 'Windows jpackage app-image' 'PASS' (Join-Path $root 'target\jpackage\SpecGenerator') -Category 'Deployment'
    } catch {
        Add-Result 'Windows jpackage app-image' 'FAIL' $_.Exception.Message -Category 'Deployment'
    }
}

$appRoot = Join-Path $root 'target\jpackage\SpecGenerator'
$appCli = Join-Path $appRoot 'SpecGenerator.exe'
$appGui = Join-Path $appRoot 'SpecGeneratorGUI.exe'
if (Test-Path -LiteralPath $appCli) {
    $appOutput = Join-Path $releaseDir 'app-cli'
    $appJob = New-ReleaseJobConfig -Mode 'JAVA' -OutputDirectory $appOutput -SourceFolder $fixtures -ModuleName 'todo'
    & $appCli generate --config $appJob *>&1 | Tee-Object -FilePath (Join-Path $releaseDir 'app-cli.log') | Out-Host
    $appExit = $LASTEXITCODE
    $appCount = Count-Workbooks -Directory $appOutput
    if ($appExit -eq 0 -and $appCount -eq 2) {
        Add-Result 'App-image CLI without Java PATH' 'PASS' "Bundled runtime generated $appCount workbook(s)." -Category 'Deployment'
    } else {
        Add-Result 'App-image CLI without Java PATH' 'FAIL' "exit=$appExit, workbooks=$appCount." -Category 'Deployment'
    }
} else {
    Add-Result 'App-image CLI without Java PATH' 'BLOCKED' "App-image CLI is missing: $appCli" -Category 'Deployment'
}

if (Test-Path -LiteralPath $appGui) {
    $process = Start-Process -FilePath $appGui -PassThru
    Start-Sleep -Seconds 5
    if ($process.HasExited) {
        Add-Result 'App-image GUI launch smoke' 'FAIL' "GUI launcher exited immediately with code $($process.ExitCode)." -Category 'Deployment'
    } else {
        Add-Result 'App-image GUI launch smoke' 'PASS' 'GUI launcher stayed alive for five seconds; this is an automated launch smoke, not human UAT.' -Category 'Deployment'
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
} else {
    Add-Result 'App-image GUI launch smoke' 'BLOCKED' "App-image GUI launcher is missing: $appGui" -Category 'Deployment'
}

Add-Result 'Network isolation' 'BLOCKED' 'No OS-level network isolation was available in this session; runtime code contains no network API, but isolation is not proven.' -Category 'Deployment'
Add-Result 'Dependency security scan' 'BLOCKED' 'No offline CVE/SBOM scanner was available in this session; dependency inventory was generated but not vulnerability-scanned.' -Category 'Deployment'

$report = Join-Path $releaseDir 'verify-release.txt'
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("Program Spec Generator release verification")
$lines.Add("Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$lines.Add("Workspace: $root")
$lines.Add("JDK: $script:jdk")
$lines.Add($javaVersion)
$lines.Add('')
foreach ($category in @('Asset', 'Functional', 'Deployment')) {
    $lines.Add("[$category]")
    foreach ($result in $script:results | Where-Object { $_.Category -eq $category }) {
        $lines.Add(("[{0}] {1}: {2}" -f $result.Status, $result.Check, $result.Detail))
    }
    $lines.Add('')
}
$functional = @($script:results | Where-Object { $_.Category -eq 'Functional' })
$deployment = @($script:results | Where-Object { $_.Category -eq 'Deployment' })
$asset = @($script:results | Where-Object { $_.Category -eq 'Asset' })
$functionalVerdict = if (@($functional | Where-Object { $_.Status -eq 'FAIL' }).Count -gt 0) { 'FAIL' } else { 'PASS' }
$deploymentFailed = @($deployment | Where-Object { $_.Status -eq 'FAIL' }).Count -gt 0
$deploymentBlocked = @($deployment | Where-Object { $_.Status -eq 'BLOCKED' }).Count -gt 0
$deploymentVerdict = if ($deploymentFailed) { 'FAIL' } elseif ($deploymentBlocked) { 'BLOCKED' } else { 'PASS' }
$assetVerdict = if (@($asset | Where-Object { $_.Status -eq 'FAIL' }).Count -gt 0) { 'FAIL (informational)' } else { 'PASS' }
$lines.Add("Functional verdict: $functionalVerdict")
$lines.Add("Deployment verdict: $deploymentVerdict")
$lines.Add("Golden asset integrity: $assetVerdict")
$lines | Set-Content -LiteralPath $report -Encoding utf8

Write-Host ''
Write-Host "Verification report: $report"
Write-Host "Functional verdict: $functionalVerdict"
Write-Host "Deployment verdict: $deploymentVerdict"
Write-Host "Golden asset integrity: $assetVerdict"
if ($functionalVerdict -eq 'FAIL' -or $deploymentFailed) {
    exit 1
}
exit 0
