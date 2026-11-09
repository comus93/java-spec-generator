# Program Spec Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** **APPROVED / 사용자 승인 2026-11-05**
**Goal:** 승인된 Java/JSP 소스 기반 Excel 프로그램 사양서 생성기를 AI·네트워크·외부 분석 실행기 없이 Windows GUI/CLI로 제공한다.
**Architecture:** Java 17+ 단일 Maven 애플리케이션에서 GUI와 CLI가 동일한 JobRunner를 사용한다. JavaParser·PMD JSP Parser가 추출한 소스 사실을 공통 WorkbookModel로 매핑하고, Apache POI가 `.xls`·`.xlsx`를 생성한다. 모듈 목록 순서에 따라 폴더별로 독립 실행하고 출력 파일은 순차 덮어쓴다.
**Tech Stack:** Java 21 개발 JDK / `--release 17`, Maven, JavaParser 3.28.2, PMD JSP 7.29.0, Apache POI HSSF/XSSF, Jackson JSON, JUnit 5, Swing, Maven Shade, `jpackage`.
**Spec:** `docs/superpowers/specs/2026-11-05-program-spec-generator-design.md` (**APPROVED 2026-11-05**, commit `b39c0ce`).
**개발 환경 확정:** **MCSVR 머신** (`C:\Workspace\github\java-spec-generator`)에서 개발한다. 사용자 지정 JDK: `C:\dev\jdk-21\bin` (`JAVA_HOME=C:\dev\jdk-21`). **2026-11-05 Paseo 사전 점검 확인:** `java` / `javac` Eclipse Temurin `21.0.13`, Maven `3.9.16` 경로 `C:\dev\apache-maven-3.9.16\bin\mvn.cmd` (해당 세션 PATH에 등록되지 않았음).

## Global Constraints

1. **구현과 실행:** Java, JDK 21 개발·빌드, 최소 JRE 17, Maven `--release 17`. GUI는 Swing, CLI는 같은 Core. 사용자 배포 환경에는 Maven이나 개발 JDK를 요구하지 않는다.
2. **분석:** JavaParser와 PMD JSP Parser를 Java 라이브러리로 직접 호출한다. 별도 분석 실행 프로세스, LLM·클라우드·네트워크 API는 사용하지 않는다. 분석 대상 Java 소스를 컴파일/실행하지 않는다.
3. **독립 모드:** 한 Job은 `JAVA` 또는 `JSP`. Controller↔JSP 연결·URL/업무 흐름 추측 금지. JSP의 `<a href>`는 반드시 `LINK_CLICK`; `<script>`, `onclick` 상세 JavaScript 분석은 1차 범위 제외.
4. **입력:** 시스템/작성자/프로그램명/개요/출력폴더/선택적 작성일 오버라이드 전역 입력. 각 모듈의 서브시스템·모듈명은 편집 가능하고 선택 폴더 경로는 읽기 전용. 동일 폴더 경로만 목록 중복 제거.
5. **폴더:** 모듈 등록 순서와 각 모듈 내 상대 경로 순서로 처리. 상위/하위 폴더 함께 등록 가능, **소스 중복 제거 없음**. 물리 출력 경로는 원본 모듈 폴더명에서부터 복제. 기존 폴더 재사용, 같은 경로 Excel은 **묻지 않고 덮어쓰기**; 그 외 파일은 삭제하지 않는다.
6. **작성일:** 사용자 지정 날짜 우선, 없으면 **소스 파일시스템 최종 수정일**. Git 수정 날짜 사용 금지. 모듈명 변경은 표제부에만 반영하고 물리 출력 경로에 영향 없음.
7. **출력:** 소스×등록 모듈별 Excel 1개 생성 시도, 기본 `.xls`, 옵션 `.xlsx`. Java **기본 9시트·정적초기화가 있으면 10시트**, JSP 5시트. `04_처리단계`와 `상세 사양`은 각각 유지한다. 표제부 10항목, 2행 설명 없음, D:F 헤더 회색, 전체 콘텐츠 윤곽선 테두리. 소스행·시작/끝행·처리단계 수·AST 종류/깊이 출력 금지, 상대 경로는 별도 경로 시트 1열만. AI 없이 기계적 DETAIL 표기만 허용한다.
8. **보안:** 승인된 파일을 읽기 전용 분석. 사용자 소스를 네트워크 전송·콘솔 덤프·로그 전문 출력하지 않는다. 심볼릭 링크/정션으로 소스 범위 밖 파일을 따라가지 않는다. 출력 루트가 소스 내부이면 시작 전에 차단한다.
9. **검증:** Task마다 변경 영향에 해당하는 **필수 기능·데이터 정합성**만 TDD로 검증하고 마지막 Task에서는 핵심 기능 통합 검증을 수행한다. 모든 추가 테스트는 승인된 기능 요구사항이나 사용자 수용 기준에 직접 연결되어야 하며, 오케스트레이터는 하위 에이전트가 작성한 테스트의 필요성을 검토한다. **JDK/Maven 설치·버전 확인, OS 권한, 심볼릭 링크/정션 생성 fixture, 별도 JVM 설치, 네트워크 단절, 의존성 감사, Golden 해시 확인 등 환경·부대 검사는 필수 기능 테스트나 릴리스 차단 조건에서 제외한다.** 실제 기능 실패는 숨기지 않는다.
10. **기준본 (2026-11-05 사용자 변경 승인):** MCSVR `docs/golden/`의 사용자 편집본 4개에 소스 인스펙션 노출 제거·서식 보강·경로 단일열 및 DETAIL 표기 규칙을 반영한다. 이 4개가 개발 기준본이며, **이전 ChatGPT ZIP 원본 해시는 더 이상 적용하지 않는다**. 확정된 새 파일 SHA-256은 `docs/test/golden-manifest.sha256`을 따른다. 정적 분석은 내부에서 행번호·AST를 사용할 수 있으나 사용자 Excel에는 노출하지 않는다.
11. **상태:** **사용자 계획 승인 완료(2026-11-05)**. 후속 사용자 지시에 따라 Paseo Orchestrator에 승인 스펙+승인 계획서의 리뷰 및 Task별 개발·검증을 위임한다. 본 문서 승인만으로 Paseo를 실행하거나 구현을 시작하지 않는다.

## Review Focus

다음은 사용자에게 제공되는 핵심 기능에 직접 연결되는 입력·출력 경계다. 개발 PC 설정과 테스트 fixture 생성 권한은 검증 범위에 포함하지 않는다.

1. **출력 경로·상위/하위 중첩:** Task 03 `JobScannerTest`에서 모듈 상대 경로 보존, 출력 폴더가 소스 내부인 잘못된 설정 거부, 동일 폴더 중복 등록 제한, 상하위 모듈 독립 순회를 검증한다. OS 심볼릭 링크·정션 fixture는 만들지 않는다.
2. **Java AST 복합 분기와 동일 행 중첩 호출:** Task 05 `JavaStepVisitorTest`에서 if/else/loop/try/return 및 동행 호출 순서가 안정적인지 검증.
3. **레거시 JSP 태그/EL/파싱 실패:** Task 06 `PmdJspAnalyzerTest`에서 form 태그, href 원문, 비표준 태그 허용 범위, 구문 오류가 파일별 `SourceAnalysisException`으로 반환됨을 검증하고 Task 10에서 다음 파일 계속 처리를 검증.
4. **Excel 수식 주입·셀 길이·무채색/2행:** Task 07 `PoiWorkbookWriterTest`에서 수식처럼 보이는 소스 원문을 문자열로 보존, `.xls` 제약 오류, 색상 값 검사.
5. **재실행 덮어쓰기:** Task 10·12에서 동일 경로 Excel의 정상 덮어쓰기(last-write-wins) 및 비대상 파일 보존을 검증한다.

---

## Implementation Structure / Interfaces

**리포지토리는 현재 제품 구현 전 초기 상태다.** 아래 경로를 명시적으로 생성하고, 임시 실험 파일과 개발 로컬 파일을 제품·커밋·배포물에 포함하지 않는다.

```text
pom.xml
src/main/java/com/comus93/specgen/
  SpecGeneratorApp.java
  config/  JobConfig.java ModuleSelection.java SourceMode.java OutputFormat.java JsonJobConfigReader.java
  scan/    JobScanner.java SourceJob.java
  facts/   SourceFacts.java JavaFacts.java JspFacts.java
  parser/  SourceAnalyzer.java SourceAnalysisException.java JavaParserAnalyzer.java JavaStepVisitor.java PmdJspAnalyzer.java
  excel/   WorkbookModel.java SheetModel.java SheetRow.java RowKind.java PoiWorkbookWriter.java
  mapper/  JavaSpecMapper.java JspSpecMapper.java
  runner/  JobRunner.java RunSummary.java FileResult.java ProgressEvent.java
  gui/     GuiLauncher.java SpecGeneratorFrame.java ModuleTableModel.java
src/test/java/com/comus93/specgen/
  config/JobConfigTest.java scan/JobScannerTest.java
  parser/JavaParserAnalyzerTest.java parser/JavaStepVisitorTest.java parser/PmdJspAnalyzerTest.java
  excel/PoiWorkbookWriterTest.java mapper/JavaSpecMapperTest.java mapper/JspSpecMapperTest.java
  runner/JobRunnerTest.java cli/SpecGeneratorAppTest.java gui/ModuleTableModelTest.java
  validation/GoldenSampleTest.java
src/test/resources/
  fixtures/java/       fixtures/jsp/
  fixtures/spring-mvc/ golden/
docs/test/golden-manifest.sha256
docs/test/fixtures-provenance.md
docs/security/dependency-inventory.md
scripts/package-windows.ps1
scripts/verify-release.ps1
```

**인터페이스 결정(각 Task에서 정의, 이후 Task에 그대로 전달):**

- `JobConfig(SourceMode mode, String system, String author, LocalDate authoringDateOverride, String programName, String overview, Path outputDirectory, OutputFormat outputFormat, List<ModuleSelection> modules)`; `ModuleSelection(Path sourceFolder, String subsystem, String moduleName)`.
- `SourceJob(ModuleSelection module, Path sourceFile, Path relativePath, Path outputFile)`. `JobScanner.plan(JobConfig config) -> List<SourceJob>`.
- `SourceFacts`는 `Path sourceFile()`을 갖는 **일반 인터페이스**다. JavaFacts와 JspFacts를 서로 다른 Task에서 순차 구현할 수 있도록 봉인하지 않는다. `SourceAnalyzer<T extends SourceFacts>.analyze(Path sourceFile) -> T`.
- `JavaFacts`의 필드/메서드/단계/호출/타입/Import 목록은 Task 04에서 정의. JSP 사실 모델은 Task 06에서 정의.
- `WorkbookModel(List<SheetModel> sheets)`; `SheetModel(String name, List<SheetRow> rows)`; `SheetRow(RowKind role, List<Object> values)`; `RowKind`는 TITLE, EMPTY, SECTION, HEADER, DATA. `Object` 셀 값은 `String|Integer|Long|LocalDate|null`로 제한.
- `JavaSpecMapper.map(JavaFacts facts, JobConfig config, SourceJob job) -> WorkbookModel`; `JspSpecMapper.map(JspFacts facts, JobConfig config, SourceJob job) -> WorkbookModel`.
- `PoiWorkbookWriter.write(WorkbookModel model, Path destination, OutputFormat format) -> void`.
- `JobRunner.run(JobConfig config, Consumer<ProgressEvent> progress) -> RunSummary`; `RunSummary`는 파일별 성공/실패/경고 수와 출력 경로를 담고 파싱 실패를 무시하지 않는다.

**지원 명령 계약(본 계획에서 확정):**

```powershell
# MCSVR Windows PowerShell에서 실행. 아래 경로는 사용자 지정 JDK 설치 위치.
$env:JAVA_HOME = 'C:\dev\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
# MCSVR 사전 점검에서 확인된 Maven 절대 경로와 세션 PATH 설정.
$env:PATH = "C:\dev\apache-maven-3.9.16\bin;$env:PATH"
$mvn = 'C:\dev\apache-maven-3.9.16\bin\mvn.cmd'
& $mvn -q -DskipTests package
& $mvn -q test
java -jar target/spec-generator.jar gui
java -jar target/spec-generator.jar generate --config "C:\work\job.json"
```

명령행 종료 코드: `0` 전부 성공/대상 없음, `1` 작업 중 일부 파일 오류, `2` 인수·설정·입력 경로 오류. 출력 파일의 내용이 성공적으로 생성되기 전에 성공으로 보고하지 않는다.

## Golden Sample 현재 보관 위치 및 최신 계약 (2026-11-05 사용자 변경 승인)

- **MCSVR 로컬 보관 폴더:** `C:\Workspace\github\java-spec-generator\docs\golden`. 사용자가 직접 지정한 위치이며 2026-11-05 확인 당시 Java 2개·JSP 2개 Excel이 존재한다. **사용자 관리 폴더이므로 파일 내용, 파일명, 위치를 임의 변경하거나 다른 폴더로 옮기지 않는다.**
- **과거 ChatGPT 첨부 원본:** `JavaParser_PMD_JSP_ProgramSpec_Samples_Grayscale.zip` (ChatGPT 세션의 `/mnt/data`); 해당 경로는 MCSVR에서 직접 접근하는 경로가 아니다. 이전의 `.local\incoming` ZIP 반입 예정 경로는 더 이상 사용하지 않는다.
- **4개 파일:** `JavaParser_TodoController_ProgramSpec_Grayscale.xlsx`, `JavaParser_TodoService_ProgramSpec_Grayscale.xlsx`, `PMD_JSP_todo_ProgramSpec_Grayscale.xlsx`, `PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx`.
- **최신 기준:** 이번 대화에서 확정한 설계 사양서 표현/서식 정책을 4개 Golden Excel에 반영했다. 실물 해시 목록은 `docs/test/golden-manifest.sha256`; 이전 ZIP 해시 사용 금지. 원본 수정 전 백업은 `.local/golden-backup/2026-11-05-pre-design-revision/`에 보존한다(커밋 제외).
- **Task 01 재개:** Golden 파일 부재와 표현 규칙 논의는 해소되었다. 사용자가 최신 Golden 내용에 대한 추가 변경을 요청하는 경우만 기준본·manifest를 다시 갱신한다. Task 01 개발 착수 자체는 별도 사용자 지시를 따른다.

## Task 01: Maven 제품 뼈대·Golden 자산 기준선

**Files**
- Create: `pom.xml`, `.gitignore` (환경·Golden 해시 전용 `BootstrapTest`는 필수 기능 범위에서 제외)
- Authoritative reference (사용자 지정): `docs/golden/{JavaParser_TodoController_ProgramSpec_Grayscale.xlsx,JavaParser_TodoService_ProgramSpec_Grayscale.xlsx,PMD_JSP_todo_ProgramSpec_Grayscale.xlsx,PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx}`. 승인된 참고 자산으로 보존하되 파일 존재·manifest 해시 자체를 기능 테스트로 검증하지 않는다.
- Create: `docs/test/golden-manifest.sha256`, `docs/test/fixtures-provenance.md`, `src/test/resources/fixtures/java/`, `src/test/resources/fixtures/jsp/`, `src/test/resources/fixtures/spring-mvc/`

**Interfaces**
- Consumes: 승인된 Spec 6, 7, 8, 13장; 사용자가 승인한 **실제 Excel 산출물**.
- Produces: Java 17 대상 Maven 컴파일·JUnit 실행이 가능한 프로젝트 뼈대와 승인된 Golden 참고 자산. 자산 해시는 기능 테스트의 PASS/FAIL 게이트가 아니다.

- [ ] **Step 1: Maven/JUnit 프로젝트 뼈대와 기능 테스트 구조를 준비한다.** Golden 파일 존재·SHA-256만 확인하는 단독 테스트는 필수 기능 테스트나 릴리스 판정에 사용하지 않는다.
- [ ] **Step 2: 개발에 필요한 Maven 컴파일·테스트 명령을 준비한다.** JDK·Maven 설치 위치나 버전 출력은 작업 준비 사항이지 제품의 테스트 항목이나 수용 기준이 아니다.
- [ ] **Step 3: Maven 설정과 기능 테스트 fixture 반입.** `maven-compiler-plugin` `release=17`, `maven-surefire-plugin`, JUnit Jupiter, JavaParser `3.28.2`, PMD JSP `7.29.0`, POI HSSF/XSSF, Jackson JSON 등 필요한 라이브러리 버전을 명시한다. 승인된 Golden 4종과 `SpringMvcStepByStep` fixture는 참고 자산으로 보존하고 실제 Java/JSP 기능 테스트의 입력으로 활용한다. 별도 자산 해시·라이선스 감사 테스트를 필수 게이트에 추가하지 않는다.
- [ ] **Step 4: `mvn -q -DskipTests package` → PASS 확인.** 이후 각 Task에서 실제 기능 입력과 기대 결과로 테스트한다. Golden 자산 해시·개발 PC 설정 때문에 기능 테스트를 BLOCKED/FAIL 처리하지 않는다.
- [ ] **Step 5: 변경 영향 테스트·스테이징 확인 후 commit.** `git add pom.xml .gitignore src/test docs/test`; `git diff --cached --check`; `git commit -m "build: bootstrap Java 17 generator and golden fixtures"`. 임시 개발 파일을 add하지 않는다.

## Task 02: 공통 설정 모델·JSON 계약·GUI 기본 행 모델

**Files**
- Create: `src/main/java/com/comus93/specgen/config/{JobConfig,ModuleSelection,SourceMode,OutputFormat,JsonJobConfigReader}.java`
- Test: `src/test/java/com/comus93/specgen/config/JobConfigTest.java`

**Interfaces**
- Consumes: Task 01 Maven, Spec 4·6·9장.
- Produces: `JobConfig`, `ModuleSelection`, `SourceMode(JAVA,JSP)`, `OutputFormat(XLS,XLSX)`; `JsonJobConfigReader.read(Path json) -> JobConfig`; 날짜 포맷 `yyyy-MM-dd`.

- [ ] **Step 1: `JobConfigTest` 작성.** `system="SYS1"`, 모듈별 `subsystem`, `moduleName`, 기본 `XLS`, nullable `authoringDateOverride`, UTF-8/한글 JSON, 잘못된 날짜·모드·빈 모듈 목록 차단을 검사.
- [ ] **Step 2: `mvn -q -Dtest=JobConfigTest test` RED 확인.**
- [ ] **Step 3: 위 record·enum 및 `JsonJobConfigReader.read(Path)` 구현.** JSON 키는 승인 Spec 9.2장에 고정된 `mode/system/author/authoringDateOverride/programName/overview/outputDirectory/outputFormat/modules[{sourceFolder,subsystem,moduleName}]`. 없는 서브시스템의 기본값은 시스템. 로컬 경로는 canonical normalization 전에 `Path`로 받는다. 불필요한 설정 키는 모르는 값으로 조용히 추측하지 않는다.
- [ ] **Step 4: `mvn -q -Dtest=JobConfigTest test` PASS.** 수정 영역에 한정.
- [ ] **Step 5: `git add src/main/java/com/comus93/specgen/config src/test/java/com/comus93/specgen/config` 후 `git commit -m "feat: define job and module configuration"`.**

## Task 03: 모듈별 재귀 스캐너·1:1 출력 경로·순차 처리 계약

**Files**
- Create: `src/main/java/com/comus93/specgen/scan/{SourceJob,JobScanner}.java`
- Test: `src/test/java/com/comus93/specgen/scan/JobScannerTest.java`

**Interfaces**
- Consumes: Task 02 `JobConfig`, `ModuleSelection`, `SourceMode`, `OutputFormat`.
- Produces: `JobScanner.plan(JobConfig) -> List<SourceJob>`, `SourceJob(module,sourceFile,relativePath,outputFile)`. 스캐너와 GUI 목록은 동일 경로 중복 방지만 공유하고, 상하위 다른 폴더는 각각 유지.

- [ ] **Step 1: `JobScannerTest` 작성.** `module1/controller/A.java` → `output/module1/controller/A.xls`, 모듈 표시명 변경 시 물리 경로 불변, Java/JSP 모드 분리, 상위·하위 폴더를 각각 별도 모듈로 처리, 동일 폴더 중복 등록 제한, 출력 폴더가 소스 내부에 있는 잘못된 설정 거부를 검증한다. 관리자 권한이 필요한 심볼릭 링크·정션 fixture는 만들지 않는다.
- [ ] **Step 2: `mvn -q -Dtest=JobScannerTest test` RED 확인.**
- [ ] **Step 3: `JobScanner.plan`과 `SourceJob` 구현.** 모듈 순서는 입력 List 순서 유지; 각 모듈 안에서는 상대 경로 **문자열 기준 안정 정렬**. `Files.walkFileTree` 기본 링크 비추적, 실제 경로의 선택 루트 이탈 차단. 출력 파일은 `sourceFile.getFileName()` 확장자만 교체. 출력 폴더 존재 여부만으로 차단하지 않는다.
- [ ] **Step 4: 필수 경로 매핑·모듈 중복 처리·출력 위치 보호 테스트 PASS.** Windows 심볼릭 링크·정션 생성 권한이나 별도 OS 환경 검사는 수행하지 않는다.
- [ ] **Step 5: `git add src/main/java/com/comus93/specgen/scan src/test/java/com/comus93/specgen/scan` → `git commit -m "feat: scan modules and preserve source hierarchy"`.**

## Task 04: JavaParser 선언/메서드/Import 원문 사실 추출

**Files**
- Create: `src/main/java/com/comus93/specgen/facts/{SourceFacts,JavaFacts}.java`
- Create: `src/main/java/com/comus93/specgen/parser/{SourceAnalyzer,SourceAnalysisException,JavaParserAnalyzer}.java`
- Test: `src/test/java/com/comus93/specgen/parser/JavaParserAnalyzerTest.java`

**Interfaces**
- Consumes: Task 03 `SourceJob.sourceFile`.
- Produces: `interface SourceFacts { Path sourceFile(); }`; `SourceAnalyzer<T extends SourceFacts>.analyze(Path) -> T`; `JavaParserAnalyzer implements SourceAnalyzer<JavaFacts>`.
- `JavaFacts`는 `sourceFile`, `typeKind`, `List<VariableFact>`, `List<MethodFact>`, `List<CallFact>`, `List<TypeRefFact>`, `List<ImportFact>`를 포함. 중첩 records: `VariableFact(kind,name,type,modifiers,lifetime,initializer,line,owner)`, `MethodFact(name,signature,parameters,returnType,visibility,startLine,endLine,javadoc,steps)`, `StepFact(sequence,kind,line,branchPath,sourceSnippet,targetId)`, `CallFact(line,callerName,calledName,targetId)`, `TypeRefFact(line,kind,name,resolvedClassId)`, `ImportFact(shortName,importPath,line)`. `steps` 초기에는 빈 List 가능.

- [ ] **Step 1: `JavaParserAnalyzerTest` 작성.** TodoController의 메서드 9개, 접근 제한자, raw parameter name/type, `logger:Log`, `service:TodoService` 필드, JavaDoc 부재 시 공란, import 원문/행을 assert. 자체 fixture로 제네릭·내부 클래스·생성자·패키지 선언 없는 소스 검사.
- [ ] **Step 2: `mvn -q -Dtest=JavaParserAnalyzerTest test` RED 확인.**
- [ ] **Step 3: JavaParser AST `CompilationUnit` Visitor 및 facts records 구현.** `SourceAnalysisException`에는 소스 경로·오류 코드·간략한 오류 위치만 남기고 원문 전체 금지. 참조 `resolvedClassId`/호출 `targetId`는 근거 없으면 `null`/빈 문자열.
- [ ] **Step 4: 해당 Parser 테스트 PASS.** 실제 소스에 Java 17 언어구문이 있어도 입력 소스를 컴파일하지 않아야 한다.
- [ ] **Step 5: 해당 경로만 `git add` → `git commit -m "feat: extract Java declarations with JavaParser"`.**

## Task 05: Java orderedSteps·분기 경로·호출 추출

**Files**
- Create: `src/main/java/com/comus93/specgen/parser/JavaStepVisitor.java`
- Modify: `src/main/java/com/comus93/specgen/parser/JavaParserAnalyzer.java`
- Test: `src/test/java/com/comus93/specgen/parser/JavaStepVisitorTest.java`, `JavaParserAnalyzerTest.java`

**Interfaces**
- Consumes: Task 04 `JavaFacts.MethodFact/StepFact/CallFact`, JavaParser `MethodDeclaration`.
- Produces: `JavaStepVisitor.extract(MethodDeclaration method) -> List<JavaFacts.StepFact>`, `JavaStepVisitor.calls(MethodDeclaration method) -> List<JavaFacts.CallFact>`. `JavaParserAnalyzer`가 해당 메서드들을 호출해 `JavaFacts`에 채운다.

- [ ] **Step 1: `JavaStepVisitorTest` 작성.** `if` true/false, loop, try/catch, nested call `service.save(foo())`, `return`, assignment, new instance, throw의 **원본 행 및 AST/토큰 기준 결정론적 순서**·branchPath 검증. 같은 행에 여러 호출이면 실제 소스 내 열 위치를 우선한다. `targetId`는 정적으로 확인되지 않으면 공란. 동일 입력 2회 결과 DeepEquals.
- [ ] **Step 2: `mvn -q -Dtest=JavaStepVisitorTest test` RED 확인.**
- [ ] **Step 3: 별도 AST Visitor로 순서를 고정하여 구현.** 전체 메서드 본문을 단순 `toString` 정렬하지 않고, 구문/표현식의 시작·끝 위치와 트리 관계를 이용해 단계 목록 및 branchPath 산출. 입력 소스의 실행 순서가 아닌 **소스에 명시된 구조상 순서**를 표현한다. 호출 결합은 문자열 일치로 추측하지 않는다.
- [ ] **Step 4: `mvn -q -Dtest=JavaStepVisitorTest,JavaParserAnalyzerTest test` PASS.** 승인 샘플 Java의 메서드 9개·처리단계 수와 변경 영향 확인.
- [ ] **Step 5: 해당 경로만 `git add` → `git commit -m "feat: extract ordered Java processing steps"`.**

## Task 06: PMD JSP Parser의 화면 필드·정적 흐름·LINK_CLICK

**Files**
- Create: `src/main/java/com/comus93/specgen/facts/JspFacts.java`
- Create: `src/main/java/com/comus93/specgen/parser/PmdJspAnalyzer.java`
- Test: `src/test/java/com/comus93/specgen/parser/PmdJspAnalyzerTest.java`
- Test fixtures: `src/test/resources/fixtures/jsp/`의 커스텀 태그/비표준 JSP 소형 fixture

**Interfaces**
- Consumes: Task 04 `SourceFacts`, `SourceAnalyzer<T>`, `SourceAnalysisException`.
- Produces: `PmdJspAnalyzer implements SourceAnalyzer<JspFacts>`. `JspFacts`는 `sourceFile`, `List<ScreenFieldFact>`, `List<FlowFact>`, `List<EventFact>`, `List<ConstantFact>`를 가진다.
- 중첩 records: `ScreenFieldFact(fieldName,object,referenceField,editable,rawAttributes,fieldType,i18nId,line)`, `FlowFact(sequence,line,nodeKind,tagOrFile,attributes,depth)`, `EventFact(type,name,description,returnValue,line)`, `ConstantFact(name,type,description,note)`.

- [ ] **Step 1: `PmdJspAnalyzerTest` 작성.** `todo.jsp`의 `form:input path="desc"`, `form:hidden path="id"`, `required`; `list-todos.jsp`의 `c:forEach items`/EL 및 **Update/Delete/Add 3개 `LINK_CLICK`** (정확한 표시 텍스트·href 원문) 검증. JavaScript 없는 JSP도 링크 이벤트 생성. 빈/알 수 없는 editable·참조 항목은 `null`; 정적 flow는 소스 순서.
- [ ] **Step 2: `mvn -q -Dtest=PmdJspAnalyzerTest test` RED 확인.**
- [ ] **Step 3: PMD JSP Parser API로 구현.** `JspLanguageModule` + `LanguageProcessorRegistry` + `TextDocument` + `ParserTask` + Node Visitor 사용; registry/document `try-with-resources`. 태그 속성·EL 문자열은 원문 보존. `LINK_CLICK`은 `<a href>`에서만 결정하며 **JS 함수/onclick 해석·링크의 Controller 연결은 금지**. PMD가 파싱 못 하는 레거시 JSP는 파일별 실패로 결과 기록할 수 있도록 예외화한다.
- [ ] **Step 4: `mvn -q -Dtest=PmdJspAnalyzerTest test` PASS.** malformed JSP 단독 실패가 다음 파일 처리까지 막는지는 Task 10에서 추가 검증.
- [ ] **Step 5: 해당 경로만 `git add` → `git commit -m "feat: extract JSP fields and link events"`.**

## Task 07: Apache POI 그레이스케일 Excel writer

**Files**
- Create: `src/main/java/com/comus93/specgen/excel/{WorkbookModel,SheetModel,SheetRow,RowKind,PoiWorkbookWriter}.java`
- Test: `src/test/java/com/comus93/specgen/excel/PoiWorkbookWriterTest.java`

**Interfaces**
- Consumes: Task 02 `OutputFormat(XLS,XLSX)`.
- Produces: `PoiWorkbookWriter.write(WorkbookModel model, Path destination, OutputFormat format)`; `WorkbookModel` 등은 위 Interface 계약과 동일. 모든 시트에서 1행 TITLE, 2행 EMPTY, 헤더는 HEADER 역할. JSP `상세 사양` 세 블록은 SECTION+HEADER+DATA의 연속으로 구성.

- [ ] **Step 1: `PoiWorkbookWriterTest` 작성.** XLS+XLSX를 POI로 다시 읽어 1행 제목, 2행 분석기 설명 없음, 제목·헤더·데이터 행, **표제부 4행 D:F 회색 렌더링**과 모든 시트 **콘텐츠 영역 윤곽선 테두리**, 회색조 전용 font/fill/border, 줄바꿈, 숫자/날짜 셀을 assert. `=SUM(1,1)` 등 원문은 FORMULA가 아니라 STRING, 셀 글자수·HSSF 행 제한 초과는 명시적으로 오류 발생. 작성 대상 외 기존 파일은 보존.
- [ ] **Step 2: `mvn -q -Dtest=PoiWorkbookWriterTest test` RED 확인.**
- [ ] **Step 3: POI HSSF/XSSF 구현.** 단일 `WorkbookModel`→형식별 Workbook을 구성. 문자색·배경색 RGB 모두 `R=G=B` 회색조, 시트별 열 너비·정렬·글자줄바꿈·섹션 병합을 기준 샘플에 맞춘다. 부모 폴더 `createDirectories`, **출력 폴더 안의 임시 파일에 완전 저장 후 `REPLACE_EXISTING` 이동**(지원 시 atomic move)하여 기존 파일을 교체하며 실패한 임시 파일은 정리. 파일 쓰기 성공 시만 완료 처리. 소스 문자열을 수식 평가하지 않는다.
- [ ] **Step 4: `mvn -q -Dtest=PoiWorkbookWriterTest test` PASS.** XLS/XLSX 각각 정상 파일 열기·시트 수를 확인.
- [ ] **Step 5: 해당 경로만 `git add` → `git commit -m "feat: render monochrome XLS and XLSX workbooks"`.**

## Task 08: Java 기본 9시트·정적초기화 포함 10시트 매퍼와 상세 사양

**Files**
- Create: `src/main/java/com/comus93/specgen/mapper/JavaSpecMapper.java`
- Test: `src/test/java/com/comus93/specgen/mapper/JavaSpecMapperTest.java`

**Interfaces**
- Consumes: Task 04·05 `JavaFacts`, Task 02 `JobConfig`, Task 03 `SourceJob`, Task 07 `WorkbookModel`.
- Produces: `JavaSpecMapper.map(JavaFacts,JobConfig,SourceJob) -> WorkbookModel`. 반환 시트의 정확한 순서는 Spec 7.2장.

- [ ] **Step 1: `JavaSpecMapperTest` 작성.** 기본 **9개 시트**: `01_표제부`, `02_OBJ정의`, `03_메서드정의`, `04_처리단계`, `05_호출관계`, `06_타입참조`, `07_Import`, `08_원본경로`, `상세 사양`. 정적 초기화가 있는 `TodoService`는 **`06_정적초기화` 추가로 10시트**이며 이후 번호 이동을 검증한다. `04_처리단계`와 `상세 사양`의 별도 존재를 검증한다. 표제부 10항목, 모듈 표시명, 작성일, 미확정 `LEVEL`·설명·resolvedClassId 공란을 확인한다. **소스행·시작행·끝행·처리 단계 수 제거, `소스 스니펫→구현 예시`, DETAIL `[Lxxx]` 제거, 경로 시트 상대 경로 1열만**을 assert한다.
- [ ] **Step 2: `mvn -q -Dtest=JavaSpecMapperTest test` RED 확인.**
- [ ] **Step 3: Java 사실의 sheet row 변환 구현.** 소스 위치·AST는 **내부 순서/부모 관계 산정 전용**이며 출력 데이터에서는 삭제한다. `DETAIL (PSEUDO CODE)`는 AI 없는 **결정론적 토큰 정규화**(`INSTANTIATE→CREATE`, `ASSIGNMENT→SET`, `BRANCH→IF`)와 원문 표현식만 사용하고 `[Lxxx]`는 출력하지 않는다. 독립 `04_처리단계`를 `상세 사양`에 통합하지 않는다. 날짜: `authoringDateOverride != null`이면 그것, 그 외 `Files.getLastModifiedTime(source).toInstant()`을 실행 PC 로컬 timezone에서 날짜로 변환. 읽기 실패 시 해당 셀은 공란, **Task 10의 JobRunner가 같은 파일 메타데이터 오류를 파일별 WARNING으로 수집**. 실행 시 상대 원본경로는 선택한 모듈 폴더 기준. **Golden의 `src/main/...` 표기는 샘플 저장소 기준의 예시 경로이므로, 비교 시 상대 경로의 구조·형식과 입력 루트 차이를 구분한다.** `Import`는 원문 선언만 기록. Data가 없으면 설명 문구 대신 빈 행/공란.
- [ ] **Step 4: `mvn -q -Dtest=JavaSpecMapperTest test` PASS.** 현행 `docs/golden/`의 TodoController(9시트), TodoService(10시트)와 fixture의 헤더·값·순서·상대 경로·DETAIL 행번호 미노출을 비교.
- [ ] **Step 5: 해당 경로만 `git add` → `git commit -m "feat: map Java facts to design-oriented specification sheets"`.**

## Task 09: JSP 5시트 매퍼와 링크 이벤트 명세

**Files**
- Create: `src/main/java/com/comus93/specgen/mapper/JspSpecMapper.java`
- Test: `src/test/java/com/comus93/specgen/mapper/JspSpecMapperTest.java`

**Interfaces**
- Consumes: Task 06 `JspFacts`, Task 02 `JobConfig`, Task 03 `SourceJob`, Task 07 `WorkbookModel`.
- Produces: `JspSpecMapper.map(JspFacts,JobConfig,SourceJob) -> WorkbookModel`.

- [ ] **Step 1: `JspSpecMapperTest` 작성.** 정확히 `01_표제부`, `02_화면필드정의`, `03_최소흐름`, `04_원본경로`, `상세 사양` 5개. 화면 필드는 **7열**(`화면 필드/Object/참조 필드/Edit/Remark(속성 원문)/항목 유형/다국어 ID`), 최소흐름은 **3열**(`순번/태그·파일/속성`), 경로 시트는 **상대 경로 1열**이다. `소스 행`·`AST 노드 종류`·`AST 깊이` 미노출도 검증한다. `상세 사양`의 **상수 및 전역 변수 / Function 및 Event / 명세 변경 사항** 세 블록. LINK_CLICK 행 `Type/Name/설명/Return`의 원문과 공란. 동적 추측 없음.
- [ ] **Step 2: `mvn -q -Dtest=JspSpecMapperTest test` RED 확인.**
- [ ] **Step 3: JSP 사실의 workbook mapping 구현.** PMD JSP AST는 **내부 추출에만** 사용하고 Excel에는 노드 타입/깊이/소스행을 작성하지 않는다. `03_최소흐름`은 정적 태그·파일 구성 순서로 출력하며 런타임 흐름이라고 주장하지 않는다. 미확인 Object/Edit/다국어 ID는 공란, `href` 표현식 유지, 행사 전역변수 임의 생성 금지, 변경 사항 블록은 공란. JavaScript 코드가 없더라도 `<a href>`는 LINK_CLICK 출력.
- [ ] **Step 4: `mvn -q -Dtest=JspSpecMapperTest test` PASS.** `list-todos.jsp` 3개 링크 이벤트와 `todo.jsp` 화면 필드 기준으로 검사.
- [ ] **Step 5: 해당 경로만 `git add` → `git commit -m "feat: map JSP facts to five specification sheets"`.**

## Task 10: 공통 JobRunner와 실제 CLI E2E

**Files**
- Create: `src/main/java/com/comus93/specgen/runner/{JobRunner,RunSummary,FileResult,ProgressEvent}.java`
- Create: `src/main/java/com/comus93/specgen/SpecGeneratorApp.java`
- Test: `src/test/java/com/comus93/specgen/runner/JobRunnerTest.java`
- Test: `src/test/java/com/comus93/specgen/cli/SpecGeneratorAppTest.java`

**Interfaces**
- Consumes: Task 02 `JobConfig`, Task 03 `JobScanner`, Task 04/06 analyzers, Task 08/09 mapper, Task 07 writer.
- Produces: `JobRunner.run(JobConfig, Consumer<ProgressEvent>) -> RunSummary`; `FileResult(sourceFile,outputFile,status,errorCode,message)` (status는 `FileStatus.SUCCESS|WARNING|FAILED`); `ProgressEvent(completed,total,sourceFile,status)`; 테스트 가능한 `SpecGeneratorApp.runCli(String[] args, PrintStream out, PrintStream err) -> int`. `main(String[])`의 `generate --config <json>` 지원을 구현하고 `gui` 분기는 Task 11에서 연결한다.

- [ ] **Step 1: `JobRunnerTest`와 `SpecGeneratorAppTest` 작성.** 두 모듈 등록 순서로 순차 분석, 일부 파일 파싱 실패 후 나머지 계속 처리, 동일 출력경로 나중 생성 결과 우선, 다른 출력 파일 보존, 실행 중 소스 해시 변화 없음, Java와 JSP 혼합 스캔 금지, 빈 폴더 0건. `runCli` 반환값으로 JSON 잘못된 인수 `2`/부분 실패 `1`/성공 `0` 검증, WARNING만 발생한 성공 실행은 `0`.
- [ ] **Step 2: `mvn -q -Dtest=JobRunnerTest,SpecGeneratorAppTest test` RED 확인.**
- [ ] **Step 3: JobRunner 구현.** `scan -> analyze -> map -> write` 파일마다 수행, 순차 처리, 파일별 실패 격리, 대상별 최종 경로/상태 요약. 작성일 파일 메타데이터를 못 읽는 경우 Excel 작성일은 공란이고 `FileResult.WARNING` 기록. `Consumer<ProgressEvent>`가 진행 UI/CLI에 알림. 로그는 상태·경로·간략 오류만 표시. CLI의 `--config`는 Task02 reader를 재사용하고 사용자 입력 외 데이터를 생성하지 않는다. `SpecGeneratorApp.main`은 CLI에 한해 `System.exit(runCli(...))`하며 GUI 진입점은 Task11에서 연결한다.
- [ ] **Step 4: `mvn -q -Dtest=JobRunnerTest,SpecGeneratorAppTest test` PASS.** 로컬 CLI 경로는 `mvn -q exec:java -Dexec.mainClass=com.comus93.specgen.SpecGeneratorApp -Dexec.args="generate --config ..."`로 통합 확인한다. 독립 실행 JAR `java -jar` 검증은 Shade가 준비되는 Task 12의 소유다.
- [ ] **Step 5: 해당 경로와 `pom.xml`만 `git add` → `git commit -m "feat: run offline source jobs via CLI"`.**

## Task 11: Swing GUI 폴더 선택·편집·진행 상태

**Files**
- Create: `src/main/java/com/comus93/specgen/gui/{GuiLauncher,SpecGeneratorFrame,ModuleTableModel}.java`
- Modify: `src/main/java/com/comus93/specgen/SpecGeneratorApp.java`
- Test: `src/test/java/com/comus93/specgen/gui/ModuleTableModelTest.java`

**Interfaces**
- Consumes: Task 02 `JobConfig`·`ModuleSelection`, Task 10 `JobRunner`·`ProgressEvent`.
- Produces: `GuiLauncher.launch() -> void` (Swing EDT에서 창 생성), `ModuleTableModel.addFolders(List<Path>)`, `ModuleTableModel.removeRow(int)`, `ModuleTableModel.updateSystem(String)`, `ModuleTableModel.toSelections() -> List<ModuleSelection>`.

- [ ] **Step 1: `ModuleTableModelTest` 작성.** 여러 폴더 드롭 누적, **완전 동일 경로만** 중복 제거, 다른 모듈 동명 및 상위/하위 폴더는 유지, 시스템 변경 시 수정하지 않은 서브시스템만 갱신, 사용자 편집 서브시스템·모듈 보존, 행 제거는 파일시스템 삭제 없음.
- [ ] **Step 2: `mvn -q -Dtest=ModuleTableModelTest test` RED 확인.**
- [ ] **Step 3: Swing UI 구현.** 공통 설정과 Java/JSP 라디오, `+ 폴더 추가`(다중 디렉터리 선택), Explorer 폴더 DnD(`javaFileListFlavor`), 편집 가능한 Subsystem/Module 컬럼, 읽기 전용 절대 소스 경로, 제거 X, 출력 폴더 선택, 작성일 선택적 오버라이드. SwingWorker로 UI thread 정지 없이 JobRunner 실행; 진행·실패 요약. GUI 테마 픽셀/색상은 추가 확정사항이 아니므로 기능 중심; **Excel 무채색과 GUI 테마 구분**.
- [ ] **Step 4: `mvn -q -Dtest=ModuleTableModelTest test` PASS.** Windows에서 `mvn -q exec:java -Dexec.mainClass=com.comus93.specgen.SpecGeneratorApp -Dexec.args="gui"`로 실행해 폴더 드롭/추가·편집·삭제·Java/JSP 결과 생성을 수동 점검한다. 헤드리스 CI에서는 창 자체를 띄우지 않는다.
- [ ] **Step 5: GUI 파일·테스트·entrypoint만 `git add` → `git commit -m "feat: provide Swing job configuration interface"`.**

## Task 12: 배포 패키지·의존성 보안·핵심 동작 통합 검증

**2026-11-09 필수 기능 검증 범위 정리:** 승인된 Design Spec §13 중 핵심 기능의 수용 기준을 우선한다. Java/JSP 사실 추출, Excel 필수 시트·주요 값·파일 재열기, CLI/GUI 기본 실행, 파일별 실패 격리, 원본 보존, 출력 덮어쓰기·비대상 파일 보존만 PASS/FAIL로 평가한다. Golden 4종·manifest는 참고 자산으로 보존하며 전체 셀/스타일 비교·자산 해시 확인은 기능 게이트가 아니다. 별도 Java 17 JVM 설치, 네트워크 단절, Windows 권한·심볼릭 링크·정션 fixture 생성, 의존성 보안·라이선스 감사 등은 필수 기능·릴리스 테스트에 포함하지 않는다. 변경 영향 외 테스트를 반복하지 않는다.

**Files**
- Modify: `pom.xml` (Shade 실행 JAR, Main-Class, 서비스 리소스 병합)
- Create: `scripts/package-windows.ps1`, `scripts/verify-release.ps1`
- Create: `docs/security/dependency-inventory.md`
- Create: `src/test/java/com/comus93/specgen/validation/GoldenSampleTest.java`
- Modify: `docs/test/golden-manifest.sha256` (필요한 실제 확보본 해시만)
- Create: `docs/operations/README.md` (GUI/CLI/offline 사용법, 배포 폴더, 입력 설정)

**Interfaces**
- Consumes: Task 01~11 산출물과 실제 Golden Sample 4종.
- Produces: `target/spec-generator.jar` 한 파일의 Java 17+ 실행형, Windows `jpackage` app-image에 GUI 및 CLI 런처, 릴리스 검증 기록.

- [ ] **Step 1: `GoldenSampleTest`와 `verify-release.ps1`의 기능 게이트 정비.** Java/JSP 실제 fixture에서 메서드·호출·처리 단계·필드·EL·링크 추출과 필수 시트·주요 값·상대 경로·내부 AST 정보 비노출을 검증한다. 생성한 Excel의 재열기 및 사용자 지정/소스 수정일 정책을 검증한다. Golden 존재·SHA-256·셀/스타일 완전 일치 검사는 기능 PASS/FAIL에 사용하지 않는다.
- [ ] **Step 2: 핵심 동작 테스트의 실패 케이스를 확인하고 수정 후 PASS를 확보한다.** Golden 파일 부재나 출력 형식 차이는 핵심 동작 테스트를 BLOCKED/FAIL로 만들지 않는다. 참고 자산의 무결성 문제는 별도 기록한다.
- [ ] **Step 3: Maven Shade(`minimizeJar=false`, 서비스 로더 리소스 유지)와 `jpackage --type app-image`를 설정한다.** Windows 배포 이미지에 런타임, GUI·CLI 런처를 포함한다. 의존성·라이선스·자산 해시는 필요시 운영 문서에 기록하되 별도 감사·버전 설치 환경 검사를 필수 테스트에 추가하지 않는다.
- [ ] **Step 4: 핵심 기능 통합 검증.** 변경 영향 테스트와 필요시 필수 기능 통합 테스트를 실행하고 빌드된 JAR로 Java/JSP CLI 및 GUI 기본 진입, Excel 재열기, 사실 추출 정확성, 오류 격리, 덮어쓰기·비대상 파일 보존을 확인한다. 별도 JRE 설치, 네트워크 단절, 관리자 권한, 링크 fixture 생성, 의존성·해시 감사용 환경은 수용 테스트에 포함하지 않는다. 결과는 `docs/operations/README.md`에 요약한다.
- [ ] **Step 5: `git diff --check`, 핵심 기능 테스트 증빙 점검 후 변경 파일만 커밋한다.** 기능 PASS/FAIL과 실제 미검증 범위를 구분하여 보고하고 환경 검사 미실시를 기능 실패로 간주하지 않는다.

## Coverage Check: Spec → Task

| 승인 스펙 범위 | 소유 Task |
|---|---|
| 1~3 목적/보안/기술 선택 | 01, 04, 06, 12 |
| 4 시스템·서브시스템·모듈·날짜·다중 폴더 | 02, 03, 11 |
| 5 파일 1:1, 계층 보존, 모듈 중첩, 덮어쓰기 | 03, 10 |
| 6 Excel 무채색·표제부 10항목·2행 삭제 | 07, 08, 09, 12 |
| 7 Java AST 내부 분석·9/10시트·처리단계/상세 사양 분리 | 04, 05, 08 |
| 8 JSP 태그/EL/링크 이벤트·5시트 | 06, 09 |
| 9 GUI/CLI 공통 실행 | 02, 10, 11 |
| 10 경로·로그·네트워크·원본 보호 | 03, 04, 06, 07, 10, 12 |
| 11 런타임 동봉 배포 기능(별도 JVM 환경 검증 제외) | 01, 12 |
| 12 파일 실패 격리·결정론·오류 | 03, 04, 06, 07, 10 |
| 13 핵심 기능 수용 AC-01~AC-10·AC-14~AC-16 | 01~12, 종합 12 |
| 15 사용자 승인된 JS 제외·작성일·덮어쓰기·중첩 폴더 | 02, 03, 06, 08, 09, 10, 12 |

## Paseo Review / Delegation Handoff (계획 승인 **후**)

**호출 정책 (다른 프로젝트에도 적용할 Codex 전역 규칙은 MCSVR `C:\Users\comus\.codex\AGENTS.md` 참조):** Orchestrator가 MCSVR 내부에서 실행 중이면 로컬 파일·Git·JDK/Maven·터미널 작업은 기본 Shell로 수행한다. 같은 머신의 터미널을 쓰기 위해 `Paseo-MCSVR` 플러그인을 경유하지 않는다. Paseo API는 실제 하위 에이전트 위임·상태 관리 등 Paseo 고유 작업에 필요한 경우에만 사용한다.

**2026-11-05 별도 플랫폼 문제:** Paseo `list_workspaces` 정상, 관리형 `create_terminal`은 `Terminal worker is not running`으로 실패(기존 Orchestrator 로그에서는 `Transport closed`). 이는 Golden Sample 부재와 별개이며, 관리형 터미널 worker가 정상화되었다는 증거 없이는 해당 기능을 정상으로 간주하지 않는다. 로컬 Shell로 가능한 업무는 플러그인 없이 실행하고, Paseo 고유 에이전트 위임 경로는 위임 전에 별도 상태 확인한다.

1. **Paseo 투입 자료**: 최신 Spec 경로와 해시, 이 Implementation Plan 경로와 해시, MCSVR `docs/golden/` Excel 4종 및 `docs/test/golden-manifest.sha256`, 테스트 fixture 출처, `main` 기준 commit. 과거 ZIP 버전으로 대체하지 않는다.
2. **1차 단계는 리뷰**: Paseo가 (a) 최신 요구사항 전부가 Task/AC에 매핑됐는지, (b) 작업 순서·인터페이스와 의존성이 실행 가능한지, (c) 보안/오프라인 경계, (d) 최신 Golden 기준본·Manifest 정합성, (e) Java17·JSP 레거시 호환성을 확인한다. **리뷰는 문서 이해가 목적이며 스펙·계획을 임의 수정하지 않는다.**
3. **리뷰 결과 보고**: 오류/모호성은 구체적인 스펙·Task ID와 수정안으로 사용자에게 보고. 승인된 요구사항 자체를 일방 변경하지 않는다. 계획 변경이 필요하면 검토받고 plan revision을 기록한다.
4. **개발 위임**: 사용자 지시가 있으면 Task 01→12를 **각 Task의 Red→Green→Refactor→커밋→변경 영향 검증** 게이트로 수행. 무단 차기 Task 전개 여부는 사용자의 그 시점 실행 지시를 따른다. 동일 파일에 여러 에이전트가 동시에 쓰지 않는다.
5. **검증 독립성**: 개발 담당과 검증 담당의 결과를 분리해 PASS/FAIL/BLOCKED 근거(실행 명령, 로그 요약, 커밋)를 받는다. 임의 중복 소스 제거·JavaScript 함수 분석 추가·출력 충돌 차단 같은 **승인되지 않은 편의 기능은 반려**한다.
6. **마무리**: 변경 영향 검증은 Task 단위, **최종 통합 테스트는 Task 12**에서만. 브랜치 병합·배포는 사용자 명시적 지시 및 최종 검증 결과에 따른다.

## Planning Self-Review / Approval Gate

- **범위**: 단일 제품 코어+두 독립 분석 경로+두 실행 표면. Java 및 JSP는 개별 테스트로 검증하고 단일 공유 출력·실행 코어로 통합한다. 이 범위를 여러 중복 스펙으로 재작성하지 않는다.
- **계약**: Task 02 `JobConfig`, Task 03 `SourceJob`, Task 04/06 `SourceFacts`, Task 07 `WorkbookModel`, Task 08/09 `SpecMapper`, Task 10 `JobRunner`만 경계를 넘는다.
- **Golden status**: MCSVR `docs/golden/` Excel 4종과 새 manifest를 최신 스펙 기준으로 관리한다. Golden 미확보 블로커는 해소됐다. 개발/런타임/배포 검증이 완료되었다는 뜻은 아니다. **이전 계획서의 정확히 Java 9시트 제약은 정적 초기화 시 10시트 예외로 수정**했다.
- **보안**: 소스 컴파일·동적 실행·외부 프로세스·런타임 다운로드·AI 전송은 금지.
- **계획 사용자 승인 완료(2026-11-05)**: 개발 머신은 MCSVR, JDK는 `C:\dev\jdk-21\bin`. **Paseo 리뷰 및 구현은 사용자의 후속 실행 지시를 받은 후에만 진행**한다.
