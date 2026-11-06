# Program Spec Generator operations

## Runtime requirements

- Java 17 or newer for `java -jar`.
- Windows 10/11 for the packaged `jpackage` app-image and Swing GUI.
- No Node.js, Python, database, Maven, or network connection is needed by the installed product.
- The application only reads selected source files and writes workbooks under the configured
  output directory. It does not execute, compile, deploy, or upload the analyzed source.

## CLI

```powershell
java -jar target\spec-generator.jar generate --config C:\work\job.json
```

Packaged CLI launcher:

```powershell
target\jpackage\SpecGenerator\SpecGenerator.exe generate --config C:\work\job.json
```

Exit codes:

- `0`: all files processed successfully; warnings are reported without failing the job.
- `1`: job completed with at least one failed file.
- `2`: invalid command line or unreadable/invalid job configuration.

## GUI

```powershell
java -jar target\spec-generator.jar gui
```

Packaged GUI launcher:

```powershell
target\jpackage\SpecGenerator\SpecGeneratorGUI.exe
```

The GUI requires a desktop session. Closing the window ends the application. The GUI and CLI use
the same scanner, analyzers, mappers, and workbook writer.

## Job configuration

```json
{
  "mode": "JAVA",
  "system": "SYS1",
  "author": "",
  "authoringDateOverride": null,
  "programName": "",
  "overview": "",
  "outputDirectory": "D:\\SpecOutput",
  "outputFormat": "xls",
  "modules": [
    {
      "sourceFolder": "C:\\SYS1_PJT1\\src\\main\\java\\ext\\sys1\\module1",
      "subsystem": "SYS1",
      "moduleName": "module1"
    }
  ]
}
```

`mode` is `JAVA` or `JSP`; `outputFormat` is `xls` or `xlsx`. A Java job processes only `.java`
files and a JSP job processes only `.jsp` files. Output hierarchy is preserved beneath the module
folder, with the source extension replaced by the workbook extension. Repeated runs overwrite only
the generated workbook for the same source; unrelated output files and source files are preserved.

## Build and package

```powershell
$env:JAVA_HOME = 'C:\dev\jdk-21'
$env:PATH = "C:\dev\jdk-21\bin;C:\dev\apache-maven-3.9.16\bin;$env:PATH"

# Full verification
& .\scripts\verify-release.ps1

# Packaging only (runs Maven package unless -SkipTests is used)
& .\scripts\package-windows.ps1 -SkipTests
```

`package-windows.ps1` creates:

- `target/spec-generator.jar`: shaded executable JAR with all compile/runtime dependencies.
- `target/jlink-runtime`: JDK 21 runtime image built with jlink.
- `target/jpackage/SpecGenerator`: Windows app-image containing CLI and GUI launchers.

`verify-release.ps1` writes a report to `target/release/verify-release.txt` and logs to
`target/release/`. Set `JAVA17_HOME` (or pass `-Java17Home`) to run the actual Java 17 CLI gate.

## Offline and safety behaviour

- Analysis is source-local. There is no LLM, RAG, Internet lookup, remote download, or telemetry.
- Source files are opened read-only; the application does not delete or rewrite them.
- Workbooks are written to a sibling temporary file and then moved into place. Failed writes leave
  the previous workbook intact.
- Cell strings are written as text, never as formulas.
- Progress output lists file paths and status codes, not source text.
- Logs and workbook output can contain analyzed source snippets; treat the output directory as
  confidential.

## Release validation record

- Date: 2026-11-06
- Candidate: `feat/task12-release-20261106` based on `5b9d043`
- Build JDK: OpenJDK 21.0.13 (Temurin-21.0.13+9)
- Maven: 3.9.16
- Commands: `mvn clean verify`, `scripts/package-windows.ps1 -SkipTests`, `scripts/verify-release.ps1`

| Gate | Result | Evidence |
|---|---|---|
| Unit/integration/release-contract tests | FAIL | `mvn clean verify` exited 1. Focused parser/mapper tests pass, but the default Golden gate now honestly checks physical blank row 3, Java core data and raw attributes against Golden. |
| Artifact build after failed test gate | PASS | `mvn -DskipTests package` succeeded; this validates packaging, not release readiness. |
| Shaded executable JAR | PASS | `target/spec-generator.jar`, 32,557,350 bytes at verification time. |
| Java 17 class-file compatibility | PASS (static) | Maximum base class-file major version 61; multi-release entries excluded from base check. |
| CLI on JDK 21 | PASS | 2 Java and 2 JSP fixtures generated, exit 0. |
| Repeated overwrite | PASS | Second run succeeded; no `.specgen-*` temporary files remained. |
| Windows app-image | PASS | `jpackage --type app-image` produced CLI and GUI launchers. |
| App-image CLI without Java PATH | PASS | Bundled runtime generated 2 workbooks. |
| App-image GUI launch smoke | PASS (smoke only) | Launcher process stayed alive for 5 seconds. This is **not human UAT**. |
| Actual Java 17 JVM | BLOCKED | No Java 17 JVM installed/configured (`JAVA17_HOME` unset; PATH has no Java). |
| Network isolation | BLOCKED | No OS-level network isolation available in the verification session. |
| Full Golden cell parity | FAIL | Required release audit reported 159 remaining cell differences. See `target/release/maven-full-golden-parity.log`. |
| Vulnerability scan | BLOCKED | No offline CVE/SBOM scanner available. |

### Remaining Golden differences (release blocker)

The default release gate now checks physical blank row 3, core data rows, paths, raw attribute
text, detail structure, grayscale styling and the cover D:F band. It fails honestly on these
remaining differences:

- Java step/detail rows keep the complete source snippet; Golden truncates one long loop snippet
  with `...`; no truncation length is defined by the spec.
- Java `06_정적초기화` preserves both a static field initializer and static-block initializers,
  while Golden omits the field initializer; the spec does not define dropping that fact.
- Java `05_호출관계` and `06_타입정보` use deterministic source order. Golden uses a different,
  non-spec ordering; the gate checks these two sheets as row multisets rather than asserting an
  arbitrary order.
- Java `05`/`06`/`07` Golden title rows are blank while the regenerated workbook has descriptive
  titles.
- JSP `03_화면구성` preserves the original attribute source text. Golden uses unquoted attributes
  for Spring form/input tags and quoted attributes for other tags; that mixed presentation has no
  general source-grounded rule.
- JSP detail section spacing differs because fixed padding rows were removed as unsupported by the
  spec.

Task 12 did not modify Golden files or the manifest, and did not claim a release PASS. The
candidate is **not release-ready** until these differences, actual Java 17 execution,
network-isolation proof, and vulnerability review are resolved.

No human UAT was performed.
