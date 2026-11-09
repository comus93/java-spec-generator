# Java Program Spec Generator

**Offline Java and JSP source analysis to generate structured Excel program specifications.**

Java Program Spec Generator helps developers, maintainers, and modernization teams document existing Java/JSP applications without executing their source code. It statically analyzes program structure and produces consistent, reviewable Excel workbooks that can serve as a starting point for technical documentation, application assessment, and legacy system analysis.

> **Current release:** [v1.0.0-rc.7 (pre-release)](https://github.com/comus93/java-spec-generator/releases/tag/v1.0.0-rc.7)  
> **Recommended download:** [Executable JAR](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.7/spec-generator-v1.0.0-rc.7.jar) · [Windows x64 package (v1.0.0-rc.1, excludes later JSP fixes)](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.1/SpecGenerator-Windows-x64-v1.0.0-rc.1.zip)  
> **Release source code:** [`v1.0.0-rc.7` tag](https://github.com/comus93/java-spec-generator/tree/v1.0.0-rc.7)

## Why this project exists

Understanding a legacy Java/JSP application often requires manually tracing controllers, services, fields, method flows, and JSP elements across many files. This project automates the repeatable parts of that work:

- **Reduce manual documentation effort.** Extract structure directly from source files rather than recreating it by hand.
- **Standardize deliverables.** Generate Excel specification workbooks with a consistent organization and presentation.
- **Support maintenance and modernization.** Provide an inspectable technical baseline for code reviews, impact analysis, and system transition planning.
- **Keep analysis local.** Process source files offline without running the application or sending source code to an AI service.

The output describes facts identifiable through static analysis. It does **not** claim to reconstruct business requirements or runtime behavior that cannot be determined from source.

## Key capabilities

| Area | Capabilities |
| --- | --- |
| Java source analysis | Extracts program structure, declarations, methods, parameters, control-flow steps, calls, and related type information using JavaParser-based analysis. |
| JSP source analysis | Uses PMD JSP parsing to capture page structure, forms, controls, expressions, links, and related elements supported by the analyzer. |
| Excel generation | Produces **one specification workbook per source file**, in **`.xls` or `.xlsx`** format, using Apache POI. |
| Organization | Preserves relative source paths in the output and supports multiple modules with system, subsystem, and module metadata. |
| Interfaces | Provides a configurable **CLI** for repeatable jobs and a **desktop GUI** for interactive use. |
| Deployment | Offers a standalone executable JAR and a Windows x64 package with a bundled Java runtime. |

## JSP recovered workbook warning cleanup (v1.0.0-rc.7)

For partially recovered JSPs, two redundant English warning rows were removed from the generated Excel `상세 사양` sheet. The run's **WARNING** status, full source-anchored recovery diagnostics in `<outputDirectory>/<system>/logs/`, and the actual extracted fields are preserved. The targeted JSP mapper tests passed **4/4**. A real rc.7 JAR mixed-JSP smoke produced **2 SUCCESS, 1 WARNING, 1 FAILED** and confirmed both messages were absent from the recovered XLSX while the log retained the warning and recovery details.

## Long Java specifications and editable default output (v1.0.0-rc.6)

Java's `상세 사양` pseudo-code text that exceeds Excel's 32,767-character cell limit is preserved across consecutive continuation rows, rather than failing the entire workbook. Long individual processing steps also have focused handling. The desktop GUI initially selects the `output` folder next to the running JAR, but the user can edit it; command-line JSON output settings take precedence for CLI runs. The existing `<outputDirectory>/<system>/java|jsp/<module>/...` and shared system-level `logs/` paths remain unchanged.

- **rc.6 focused tests:** 103 passed, 0 failed, 3 skipped in the unelevated Herdr/OMP process. Those three symbolic-link creation tests previously passed in the elevated AgentDock process and do not cover the Java text fix. The required privilege is absent from the OMP process token; manual GUI visual verification remains pending.

## Output layout by system and analysis mode (v1.0.0-rc.5)

Workbooks now go under `<outputDirectory>/<system>/java/<module>/...` or `<outputDirectory>/<system>/jsp/<module>/...`, preventing collisions when Java and JSP sources share module and file names. Both modes write timestamped logs to the same `<outputDirectory>/<system>/logs/` directory. The selected output folder is still the base directory; the system name must be a valid single folder name. Existing older output locations are not automatically migrated.

## JSP recovery and diagnostics (v1.0.0-rc.4)

When PMD cannot parse a legacy JSP, the application can conservatively recover source-anchored facts and generate an **incomplete, explicitly marked WARNING workbook**, rather than losing all extractable information. Unrecoverable files are reported as **FAILED**, while the remaining batch continues.

The GUI displays concise one-line warnings and errors. A UTF-8 `.log` for each run is stored under `<outputDirectory>/logs/` with full parser diagnostics, original-source excerpts, recovery limitations, and outcome counts. **Logs may contain confidential source snippets.**

This is a bounded recovery mechanism, not a general permissive JSP parser; uncertain hierarchy and conditional semantics are not invented. Automated GUI message-format tests passed, but manual visual GUI acceptance testing remains outstanding.

## How it works

```text
Java / JSP source folders
           |
           v
     File discovery
           |
           v
  JavaParser / PMD JSP
           |
           v
  Structured source facts
           |
           v
  Specification mapping
           |
           v
   Apache POI workbook
           |
           v
  Excel specifications
      (.xls / .xlsx)
```

Source analysis and workbook generation run locally. Input source files are read without compilation or execution, and generated workbooks are written to the configured output directory.

## Get started

### Option A: Executable JAR (recommended for existing Java environments)

**Requirements:** Java 17 or newer. The release was built for Java 17 bytecode compatibility and independently executed on JDK 21.

1. Download the [JAR](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.7/spec-generator-v1.0.0-rc.7.jar).
2. Download the [Java job template](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.7/sample-java-job.json) or [JSP job template](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.7/sample-jsp-job.json).
3. Edit the template to specify an existing source folder and an output directory **outside** that source folder.
4. Run the generator from a terminal.

```bash
java -version
java -jar spec-generator-v1.0.0-rc.7.jar generate --config sample-java-job.json
```

For JSP files, run a separate job using `sample-jsp-job.json`:

```bash
java -jar spec-generator-v1.0.0-rc.7.jar generate --config sample-jsp-job.json
```

To open the graphical interface:

```bash
java -jar spec-generator-v1.0.0-rc.7.jar gui
```

The executable JAR requires no Maven installation. The GUI requires a desktop environment. Windows paths in JSON must use escaped backslashes (for example, `C:\\Project\\src\\main\\java`).

### Option B: Windows package (Java installation not required)

1. Download the [Windows x64 ZIP](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.1/SpecGenerator-Windows-x64-v1.0.0-rc.1.zip).
2. Extract the **entire archive**; retain the accompanying `app` and `runtime` directories.
3. Run `SpecGenerator/SpecGeneratorGUI.exe`, or use `SpecGenerator/SpecGenerator.exe` for CLI jobs.

The Windows package includes its own Java runtime. **Note:** The Windows ZIP remains at v1.0.0-rc.1 and does not include later JSP parser/recovery/logging or system/mode output layout changes. Use the v1.0.0-rc.7 JAR for the new behavior.

For full instructions, configuration fields, sample commands, and exit codes, see the **[Release README](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.7/README.md)**.

## Example job configuration

```json
{
  "mode": "JAVA",
  "system": "SYSTEM_A",
  "author": "",
  "authoringDateOverride": null,
  "programName": "",
  "overview": "",
  "outputDirectory": "C:\\SpecOutput\\Java",
  "outputFormat": "xlsx",
  "modules": [
    {
      "sourceFolder": "C:\\Project\\src\\main\\java",
      "subsystem": "SUBSYSTEM_A",
      "moduleName": "java"
    }
  ]
}
```

Change `mode` to `JSP` and supply a JSP source folder for JSP analysis. Multiple module entries can be supplied in one job. Existing workbooks at the same output paths are replaced when a job is rerun.

## Technology

The released implementation is a **Java 17-targeted, Maven-based** application using:

- **JavaParser** for Java syntax analysis
- **PMD JSP** for JSP parsing
- **Apache POI** for Excel workbook generation
- **Jackson** for JSON configuration
- **Swing** for the desktop user interface
- **JUnit 5** for automated tests

The released source is organized into scanner, parser, structured-facts, mapper, Excel writer, runner, configuration, and GUI components.

## Verification and release status

**v1.0.0-rc.7 is a pre-release**, not a general-availability or production-certified release.

- **Output layout patch (v1.0.0-rc.5): 89/89 targeted tests passed, 0 failed, 0 skipped on an AgentDock-run MCSVR retest.** The original OMP-run test process skipped three Windows symbolic-link cases because of insufficient symlink privileges; all three passed when rerun with an authorized AgentDock execution context. JAR smoke verified identical Java/JSP module and source names generated separate Excel workbooks plus shared system-level logs.
- The **v1.0.0-rc.4 JSP recovery** was previously validated with 43 focused tests and a mixed valid/recovered/failed batch.
- **91 automated Maven tests passed** on the previous v1.0.0-rc.1 baseline; the full suite was not rerun for this focused patch.
- An independent end-to-end test processed **8 Java and 5 JSP files** from [SpringMvcStepByStep](https://github.com/in28minutes/SpringMvcStepByStep), generating **26 Excel workbooks** across the two supported formats.
- The output structure and source-to-workbook mapping were checked; the Windows packaged CLI was also smoke-tested without Java on `PATH`.
- End-user GUI acceptance testing, an actual Java 17 runtime execution check, non-Windows validation, customer-specific legacy JSP coverage, and security/vulnerability assessments remain outside the completed verification scope.

See the [previous baseline E2E report](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.1/E2E_REPORT.md) and [current release notes](https://github.com/comus93/java-spec-generator/releases/tag/v1.0.0-rc.7) for details.

## Repository and versioning note

The **default `main` branch and the tagged release are not yet the same implementation baseline**. For the verified release source, use the [`v1.0.0-rc.7` tag](https://github.com/comus93/java-spec-generator/tree/v1.0.0-rc.7) rather than assuming the current `main` branch reproduces the published binaries.

The tool generates documentation based on static source facts. Review the generated specifications before using them in critical engineering or business decisions.
