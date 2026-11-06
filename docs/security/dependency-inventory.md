# Dependency inventory and SBOM record

- Generated: 2026-11-06
- Task: Task 12 packaging and release validation
- Build JDK: OpenJDK 21.0.13 (Temurin-21.0.13+9)
- Build Maven: Apache Maven 3.9.16
- Source commit baseline: `5b9d043`
- Runtime target: Java 17 class files (`--release 17`, class-file major 61)
- Resolved dependency source: `mvn dependency:tree` (`target/release/dependency-tree.txt` in a verification run)

## Direct dependencies

| Component | Version | Scope | Purpose | License metadata |
|---|---|---|---|---|
| `com.github.javaparser:javaparser-core` | 3.28.2 | compile | Java AST analysis | LGPL / Apache-2.0 (POM declares both) |
| `net.sourceforge.pmd:pmd-core` | 7.29.0 | compile | PMD parser infrastructure | PMD BSD-style (not declared in the POM) |
| `net.sourceforge.pmd:pmd-jsp` | 7.29.0 | compile | JSP AST analysis | PMD BSD-style (not declared in the POM) |
| `org.apache.poi:poi` | 5.4.1 | compile | HSSF `.xls` writer | Apache-2.0 |
| `org.apache.poi:poi-ooxml` | 5.4.1 | compile | XSSF `.xlsx` writer | Apache-2.0 |
| `com.fasterxml.jackson.core:jackson-databind` | 2.18.2 | compile | JSON job configuration | Apache-2.0 |
| `org.slf4j:slf4j-api` | 2.0.16 | compile | Logging facade | MIT (not declared in the POM) |
| `org.slf4j:slf4j-simple` | 2.0.16 | runtime | Offline logging binding | MIT (not declared in the POM) |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | test | Test framework only; not packaged | EPL-2.0 |

Only compile/runtime dependencies are included in the shaded JAR. Test dependencies are not
present in `target/spec-generator.jar`.

## Transitive dependency inventory

```text
com.comus93:spec-generator:jar:1.0.0-SNAPSHOT
+- com.github.javaparser:javaparser-core:jar:3.28.2:compile
+- net.sourceforge.pmd:pmd-core:jar:7.29.0:compile
|  +- org.slf4j:jul-to-slf4j:jar:1.7.36:compile
|  +- org.antlr:antlr4-runtime:jar:4.13.2:compile
|  +- net.sf.saxon:Saxon-HE:jar:12.10:compile
|  |  +- org.xmlresolver:xmlresolver:jar:5.3.3:compile
|  |  \- org.xmlresolver:xmlresolver:jar:data:5.3.3:compile
|  +- org.apache.commons:commons-lang3:jar:3.21.0:compile
|  +- commons-io:commons-io:jar:2.22.0:compile
|  +- org.ow2.asm:asm:jar:9.11.1:compile
|  +- com.google.code.gson:gson:jar:2.15.0:compile
|  |  \- com.google.errorprone:error_prone_annotations:jar:2.50.0:compile
|  +- org.checkerframework:checker-qual:jar:3.55.1:compile
|  +- org.pcollections:pcollections:jar:4.0.2:compile
|  \- com.github.oowekyala.ooxml:nice-xml-messages:jar:3.1:compile
+- net.sourceforge.pmd:pmd-jsp:jar:7.29.0:compile
+- org.apache.poi:poi:jar:5.4.1:compile
|  +- commons-codec:commons-codec:jar:1.18.0:compile
|  +- org.apache.commons:commons-collections4:jar:4.4:compile
|  +- org.apache.commons:commons-math3:jar:3.6.1:compile
|  +- com.zaxxer:SparseBitSet:jar:1.3:compile
|  \- org.apache.logging.log4j:log4j-api:jar:2.24.3:compile
+- org.apache.poi:poi-ooxml:jar:5.4.1:compile
|  +- org.apache.poi:poi-ooxml-lite:jar:5.4.1:compile
|  +- org.apache.xmlbeans:xmlbeans:jar:5.3.0:compile
|  +- org.apache.commons:commons-compress:jar:1.27.1:compile
|  \- com.github.virtuald:curvesapi:jar:1.08:compile
+- com.fasterxml.jackson.core:jackson-databind:jar:2.18.2:compile
|  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.18.2:compile
|  \- com.fasterxml.jackson.core:jackson-core:jar:2.18.2:compile
+- org.slf4j:slf4j-api:jar:2.0.16:compile
\- org.slf4j:slf4j-simple:jar:2.0.16:runtime
```

### Declared transitive license metadata

| Component | License metadata found in the local Maven POM |
|---|---|
| `org.antlr:antlr4-runtime` | Not declared; upstream ANTLR is BSD-3-Clause |
| `net.sf.saxon:Saxon-HE` | MPL-2.0 |
| `org.xmlresolver:xmlresolver` | Apache-2.0 |
| `org.apache.commons:commons-lang3` | Not declared; upstream is Apache-2.0 |
| `commons-io:commons-io` | Not declared; upstream is Apache-2.0 |
| `org.ow2.asm:asm` | BSD-3-Clause |
| `com.google.code.gson:gson` | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations` | Apache-2.0 |
| `org.checkerframework:checker-qual` | MIT |
| `org.pcollections:pcollections` | MIT |
| `com.github.oowekyala.ooxml:nice-xml-messages` | MIT |
| `commons-codec:commons-codec` | Not declared; upstream is Apache-2.0 |
| `org.apache.commons:commons-collections4` | Not declared; upstream is Apache-2.0 |
| `org.apache.commons:commons-math3` | Not declared; upstream is Apache-2.0 |
| `com.zaxxer:SparseBitSet` | Apache-2.0 |
| `org.apache.logging.log4j:log4j-api` | Apache-2.0 |
| `org.apache.xmlbeans:xmlbeans` | Not declared; upstream is Apache-2.0 |
| `org.apache.commons:commons-compress` | Not declared; upstream is Apache-2.0 |
| `com.github.virtuald:curvesapi` | BSD-style |

License review remains an operator responsibility before redistribution. Formulas and fonts are
not downloaded at runtime; all Java dependencies are bundled in the shaded JAR.

## SBOM (CycloneDX-compatible component inventory)

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "version": 1,
  "metadata": {
    "component": {
      "type": "application",
      "group": "com.comus93",
      "name": "spec-generator",
      "version": "1.0.0-SNAPSHOT"
    }
  },
  "components": [
    { "type": "library", "group": "com.github.javaparser", "name": "javaparser-core", "version": "3.28.2", "scope": "compile" },
    { "type": "library", "group": "net.sourceforge.pmd", "name": "pmd-core", "version": "7.29.0", "scope": "compile" },
    { "type": "library", "group": "org.slf4j", "name": "jul-to-slf4j", "version": "1.7.36", "scope": "compile" },
    { "type": "library", "group": "org.antlr", "name": "antlr4-runtime", "version": "4.13.2", "scope": "compile" },
    { "type": "library", "group": "net.sf.saxon", "name": "Saxon-HE", "version": "12.10", "scope": "compile" },
    { "type": "library", "group": "org.xmlresolver", "name": "xmlresolver", "version": "5.3.3", "scope": "compile" },
    { "type": "library", "group": "org.apache.commons", "name": "commons-lang3", "version": "3.21.0", "scope": "compile" },
    { "type": "library", "group": "commons-io", "name": "commons-io", "version": "2.22.0", "scope": "compile" },
    { "type": "library", "group": "org.ow2.asm", "name": "asm", "version": "9.11.1", "scope": "compile" },
    { "type": "library", "group": "com.google.code.gson", "name": "gson", "version": "2.15.0", "scope": "compile" },
    { "type": "library", "group": "com.google.errorprone", "name": "error_prone_annotations", "version": "2.50.0", "scope": "compile" },
    { "type": "library", "group": "org.checkerframework", "name": "checker-qual", "version": "3.55.1", "scope": "compile" },
    { "type": "library", "group": "org.pcollections", "name": "pcollections", "version": "4.0.2", "scope": "compile" },
    { "type": "library", "group": "com.github.oowekyala.ooxml", "name": "nice-xml-messages", "version": "3.1", "scope": "compile" },
    { "type": "library", "group": "net.sourceforge.pmd", "name": "pmd-jsp", "version": "7.29.0", "scope": "compile" },
    { "type": "library", "group": "org.apache.poi", "name": "poi", "version": "5.4.1", "scope": "compile" },
    { "type": "library", "group": "commons-codec", "name": "commons-codec", "version": "1.18.0", "scope": "compile" },
    { "type": "library", "group": "org.apache.commons", "name": "commons-collections4", "version": "4.4", "scope": "compile" },
    { "type": "library", "group": "org.apache.commons", "name": "commons-math3", "version": "3.6.1", "scope": "compile" },
    { "type": "library", "group": "com.zaxxer", "name": "SparseBitSet", "version": "1.3", "scope": "compile" },
    { "type": "library", "group": "org.apache.logging.log4j", "name": "log4j-api", "version": "2.24.3", "scope": "compile" },
    { "type": "library", "group": "org.apache.poi", "name": "poi-ooxml", "version": "5.4.1", "scope": "compile" },
    { "type": "library", "group": "org.apache.poi", "name": "poi-ooxml-lite", "version": "5.4.1", "scope": "compile" },
    { "type": "library", "group": "org.apache.xmlbeans", "name": "xmlbeans", "version": "5.3.0", "scope": "compile" },
    { "type": "library", "group": "org.apache.commons", "name": "commons-compress", "version": "1.27.1", "scope": "compile" },
    { "type": "library", "group": "com.github.virtuald", "name": "curvesapi", "version": "1.08", "scope": "compile" },
    { "type": "library", "group": "com.fasterxml.jackson.core", "name": "jackson-databind", "version": "2.18.2", "scope": "compile" },
    { "type": "library", "group": "com.fasterxml.jackson.core", "name": "jackson-annotations", "version": "2.18.2", "scope": "compile" },
    { "type": "library", "group": "com.fasterxml.jackson.core", "name": "jackson-core", "version": "2.18.2", "scope": "compile" },
    { "type": "library", "group": "org.slf4j", "name": "slf4j-api", "version": "2.0.16", "scope": "compile" },
    { "type": "library", "group": "org.slf4j", "name": "slf4j-simple", "version": "2.0.16", "scope": "runtime" },
    { "type": "library", "group": "org.junit.jupiter", "name": "junit-jupiter", "version": "5.11.4", "scope": "test" },
    { "type": "library", "group": "org.junit.jupiter", "name": "junit-jupiter-api", "version": "5.11.4", "scope": "test" },
    { "type": "library", "group": "org.opentest4j", "name": "opentest4j", "version": "1.3.0", "scope": "test" },
    { "type": "library", "group": "org.junit.platform", "name": "junit-platform-commons", "version": "1.11.4", "scope": "test" },
    { "type": "library", "group": "org.apiguardian", "name": "apiguardian-api", "version": "1.1.2", "scope": "test" },
    { "type": "library", "group": "org.junit.jupiter", "name": "junit-jupiter-params", "version": "5.11.4", "scope": "test" },
    { "type": "library", "group": "org.junit.jupiter", "name": "junit-jupiter-engine", "version": "5.11.4", "scope": "test" },
    { "type": "library", "group": "org.junit.platform", "name": "junit-platform-engine", "version": "1.11.4", "scope": "test" }
  ]
}
```

## Artifact hashes (verified build)

| Artifact | SHA-256 |
|---|---|
| `target/spec-generator.jar` | `be4dc689d0bb051743814663437444910c023137ccf2cf14eda5810f9c318240` |
| `target/jpackage/SpecGenerator/SpecGenerator.exe` (CLI launcher) | `b987164e33cd942bfa8657e5a4a2ec953404783fa4f7b5b70b1c6576112c679a` |
| `target/jpackage/SpecGenerator/SpecGeneratorGUI.exe` (GUI launcher) | `537c9bc4c9d717b0c04ef55ae008a5a7e19becea6f591e347c310871d94fdc64` |
| `target/jpackage/SpecGenerator/app/spec-generator.jar` | `be4dc689d0bb051743814663437444910c023137ccf2cf14eda5810f9c318240` |
| `target/jpackage/SpecGenerator/app/SpecGenerator.cfg` | `6dc3a2d7dfdcaadff363f621273b33e3b7478ce0001fcb4d88cf1c572c98870f` |
| `target/jpackage/SpecGenerator/app/SpecGeneratorGUI.cfg` | `b86be8d7390acb26bc1e970c9ad64b612ad8a24943a671e2cc9890466b2b237f` |

The app-image contains a JDK 21 runtime produced by `jlink --add-modules ALL-MODULE-PATH`.
The shaded JAR's maximum base class-file major version is 61 (Java 17). Entries under
`META-INF/versions/` are multi-release variants and were excluded from that base check.

## Security review status

- **Actual Java 17 JVM execution: BLOCKED.** No Java 17 JVM is installed or configured in this
  workspace. The JAR was byte-checked for Java 17 class files and ran on JDK 21, but that is not a
  substitute for running on an actual Java 17 runtime.
- **Network isolation: BLOCKED.** The application code contains no network client or download path,
  but this session could not enforce an OS-level network block. Offline behaviour is therefore
  documented as a design property, not proven by isolation testing.
- **Vulnerability scan: BLOCKED.** No offline CVE/SBOM scanner was available. Versions are pinned,
  and the complete resolved inventory is recorded above.
