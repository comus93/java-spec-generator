# Java Program Spec Generator

**Offline Java and JSP source analysis to generate structured Excel program specifications.**

Java Program Spec Generator helps developers, maintainers, and modernization teams document existing Java/JSP applications without executing their source code. It statically analyzes program structure and produces consistent, reviewable Excel workbooks that can serve as a starting point for technical documentation, application assessment, and legacy system analysis.

> **Current release:** [v1.0.0-rc.2 (pre-release)](https://github.com/comus93/java-spec-generator/releases/tag/v1.0.0-rc.2)  
> **Recommended download:** [Executable JAR](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.2/spec-generator-v1.0.0-rc.2.jar) · [Windows x64 package (v1.0.0-rc.1, no DOCTYPE fix)](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.1/SpecGenerator-Windows-x64-v1.0.0-rc.1.zip)  
> **Release source code:** [`v1.0.0-rc.2` tag](https://github.com/comus93/java-spec-generator/tree/v1.0.0-rc.2)

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

1. Download the [JAR](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.2/spec-generator-v1.0.0-rc.2.jar).
2. Download the [Java job template](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.2/sample-java-job.json) or [JSP job template](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.2/sample-jsp-job.json).
3. Edit the template to specify an existing source folder and an output directory **outside** that source folder.
4. Run the generator from a terminal.

```bash
java -version
java -jar spec-generator-v1.0.0-rc.2.jar generate --config sample-java-job.json
```

For JSP files, run a separate job using `sample-jsp-job.json`:

```bash
java -jar spec-generator-v1.0.0-rc.2.jar generate --config sample-jsp-job.json
```

To open the graphical interface:

```bash
java -jar spec-generator-v1.0.0-rc.2.jar gui
```

The executable JAR requires no Maven installation. The GUI requires a desktop environment. Windows paths in JSON must use escaped backslashes (for example, `C:\\Project\\src\\main\\java`).

### Option B: Windows package (Java installation not required)

1. Download the [Windows x64 ZIP](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.1/SpecGenerator-Windows-x64-v1.0.0-rc.1.zip).
2. Extract the **entire archive**; retain the accompanying `app` and `runtime` directories.
3. Run `SpecGenerator/SpecGeneratorGUI.exe`, or use `SpecGenerator/SpecGenerator.exe` for CLI jobs.

The Windows package includes its own Java runtime. **Note:** The Windows ZIP remains at v1.0.0-rc.1 and does not include the JSP DOCTYPE fix. Use the v1.0.0-rc.2 JAR for affected JSP sources.

For full instructions, configuration fields, sample commands, and exit codes, see the **[Release README](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.2/README.md)**.

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

**v1.0.0-rc.2 is a pre-release**, not a general-availability or production-certified release.

- **12 focused JSP parser tests passed** for the v1.0.0-rc.2 patch, including the reported DOCTYPE failure. The updated JAR generated **3/3 JSP workbooks** with input files unchanged.
- **91 automated Maven tests passed** on the previous v1.0.0-rc.1 baseline; the full suite was not rerun for this focused patch.
- An independent end-to-end test processed **8 Java and 5 JSP files** from [SpringMvcStepByStep](https://github.com/in28minutes/SpringMvcStepByStep), generating **26 Excel workbooks** across the two supported formats.
- The output structure and source-to-workbook mapping were checked; the Windows packaged CLI was also smoke-tested without Java on `PATH`.
- End-user GUI acceptance testing, an actual Java 17 runtime execution check, non-Windows validation, customer-specific legacy JSP coverage, and security/vulnerability assessments remain outside the completed verification scope.

See the [previous baseline E2E report](https://github.com/comus93/java-spec-generator/releases/download/v1.0.0-rc.1/E2E_REPORT.md) and [current release notes](https://github.com/comus93/java-spec-generator/releases/tag/v1.0.0-rc.2) for details.

## Repository and versioning note

The **default `main` branch and the tagged release are not yet the same implementation baseline**. For the verified release source, use the [`v1.0.0-rc.2` tag](https://github.com/comus93/java-spec-generator/tree/v1.0.0-rc.2) rather than assuming the current `main` branch reproduces the published binaries.

The tool generates documentation based on static source facts. Review the generated specifications before using them in critical engineering or business decisions.
