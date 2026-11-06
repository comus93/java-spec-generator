# Program Spec Generator Implementation Plan

> **For implementation agents (2026-11-06 효율화 개정):** 승인된 Spec과 이 계획서의 Task 범위를 따르되, **Task 07~12는 아래 경량 실행 게이트**를 사용한다. Superpowers `executing-plans`는 필요 시 활용하고, 별도 세부 계획서 재작성·상시 하위 에이전트/검증 에이전트 생성은 요구하지 않는다. `subagent-driven-development`는 실제 병렬 이득과 사용자 승인 범위가 있을 때만 선택한다.

**Status:** **APPROVED / 사용자 승인 2026-11-05** · **실행/검증 절차 경량화 개정 2026-11-06 (사용자 지시)**. 기능 Spec과 Golden 기준은 변경하지 않으며, 이미 완료된 Task 01~06의 작업 기록·수용 기준은 소급 변경하지 않는다.
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
9. **검증:** Task 07~11은 **핵심 수용 사례 중심의 대상 테스트**만 작성·실행하고 Task당 **최종 통과 확인 1회**를 기본으로 한다. 동작을 새로 만들 때는 유의미한 실패(RED)를 한 번 확인하되 컴파일 실패를 만들기 위한 형식적 RED·매번 반복 빌드/회귀 테스트는 요구하지 않는다. 실제 실패 수정 후 해당 테스트 재실행은 필수다. 전체 테스트·Golden 정합성·Java17/오프라인/GUI·배포 통합 검증은 Task 12에서 묶어서 한 번 수행한다. 테스트 삭제·실패 은폐·필수 계약의 무검증 통과 선언은 금지한다.
10. **기준본 (2026-11-05 사용자 변경 승인):** MCSVR `docs/golden/`의 사용자 편집본 4개에 소스 인스펙션 노출 제거·서식 보강·경로 단일열 및 DETAIL 표기 규칙을 반영한다. 이 4개가 개발 기준본이며, **이전 ChatGPT ZIP 원본 해시는 더 이상 적용하지 않는다**. 확정된 새 파일 SHA-256은 `docs/test/golden-manifest.sha256`을 따른다. 정적 분석은 내부에서 행번호·AST를 사용할 수 있으나 사용자 Excel에는 노출하지 않는다.
11. **상태:** **사용자 계획 승인 완료(2026-11-05)**. 후속 사용자 지시에 따라 Paseo Orchestrator에 승인 스펙+승인 계획서의 리뷰 및 Task별 개발·검증을 위임한다. 본 문서 승인만으로 Paseo를 실행하거나 구현을 시작하지 않는다.

## Task 07~12 경량 개발·검증 규칙 (2026-11-06 개정)

이 절이 아래 잔여 Task의 **실행 방법에 우선 적용**된다. 기존 Task 01~06의 세부 Step·커밋은 과거 실행 기준으로 보존하고, 승인된 **제품 기능·보안·Golden 수용 기준 자체는 축소하지 않는다.**

1. **검증 목적을 나눈다:** Task 07~11은 담당 API의 **핵심 불변 조건과 실패 시 피해가 큰 경계**를 작은 대표 fixture로 검사한다. 전체 회귀·전 시트 Golden·오프라인/JRE·GUI 패키징은 Task 12가 단일 소유자다.
2. **작업 흐름은 3단계:** ① 핵심 검증 사례 ② 구현 ③ 대상 테스트 PASS·diff check·커밋. 신규 동작은 가능하면 테스트를 먼저 쓰고 실패(RED)를 **한 번만** 확인한다. 테스트나 툴 환경을 일부러 깨서 RED를 연출하거나, 코드 몇 줄마다 Maven을 돌리지 않는다.
3. **실행 게이트는 한 번씩:** 해당 Task의 단일 Maven `-Dtest=...` 명령을 **완료 시점에 한 번** 실행한다. 실패하면 수정된 범위만 재검증한다. 변경과 무관한 전체 `mvn test`·`package`·Golden 비교를 매 Task/수정마다 중복 실행하지 않는다.
4. **빠르게 만들되 의미는 확인한다:** 테스트 개수·행 수가 아닌 **필드 의미/실제 결과**를 검증한다. 특히 `LINK_CLICK`은 `description=href 원문`, `returnValue=공란`; JSP 폼 Object, include 파일 경로, 알려진 필드/EL은 누락시키지 않는다. 필요한 사실이 이전 Parser에 없다면 소비 Task에서 영향을 받는 Parser 테스트까지 **좁게 보강**한다.
5. **추가 검토 최소화:** Task마다 별도 QA 에이전트, 리뷰 계획서, 장문의 작업 보고서, 수동 전체 화면 점검을 의무화하지 않는다. 완료 보고는 변경 파일·대상 테스트 명령/결과·미검증 위험·커밋 한 단락이면 충분하다. **독립 검증이 필요하면 사용자 지시 또는 Task12 최종 게이트에서** 수행한다.
6. **예외/중단:** 보안, 파일 덮어쓰기, 원본 훼손, 계약 위반이 의심되면 관련 표적 테스트를 추가한다. 환경 장애나 기준 자산 부재를 PASS로 처리하지 않고 BLOCKED 또는 미검증으로 보고한다. 다음 Task로의 자동 진행·병합·배포는 기존 사용자 승인 원칙을 따른다.

**검증 비용과 품질을 함께 측정한다.** Task 06 벤치마크에서 자체 PASS만으로는 필드/이벤트 매핑 품질이 보장되지 않았다. 따라서 **자체 테스트는 경량화하되 핵심 출력 검증을 없애지 않는다.**

## Review Focus

다음은 설계상 실패하기 쉽고 의도적으로 소유 Task의 테스트에서 다뤄야 하는 입력/경계다.

1. **Windows 폴더 경로·정션·상위/하위 중첩:** Task 03 `JobScannerTest`에서 실경로 밖 이동 차단, 동일 경로 중복 등록 제한, 상하위 독립 순회를 모두 검증.
2. **Java AST 복합 분기와 동일 행 중첩 호출:** Task 05 `JavaStepVisitorTest`에서 if/else/loop/try/return 및 동행 호출 순서가 안정적인지 검증.
3. **레거시 JSP 태그/EL/파싱 실패:** Task 06 `PmdJspAnalyzerTest`에서 form 태그, href 원문, 비표준 태그 허용 범위, 구문 오류가 파일별 `SourceAnalysisException`으로 반환됨을 검증하고 Task 10에서 다음 파일 계속 처리를 검증.
4. **Excel 수식 주입·셀 길이·무채색/2행:** Task 07 `PoiWorkbookWriterTest`에서 수식처럼 보이는 소스 원문을 문자열로 보존, `.xls` 제약 오류, 색상 값 검사.
5. **Java 17/오프라인/기존 파일 덮어쓰기:** Task 10·12 `JobRunnerTest` 및 `scripts/verify-release.ps1`에서 Java 17 JVM 실행, 동일 경로 last-write-wins, 네트워크 격리 상태 실행을 검증.

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
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
# MCSVR 사전 점검에서 확인된 Maven 절대 경로와 세션 PATH 설정.
$env:PATH = "C:\dev\apache-maven-3.9.16\bin;$env:PATH"
$mvn = 'C:\dev\apache-maven-3.9.16\bin\mvn.cmd'
& $mvn -version
# 참조 명령: 실제 실행 시점은 위 경량 검증 규칙에 따른다. Task마다 전부 돌리지 않는다.
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

## Task 01: Maven 제품 뼈대·JRE 17 게이트·Golden 자산 기준선

**Files**
- Create: `pom.xml`, `.gitignore`, `src/test/java/com/comus93/specgen/BootstrapTest.java`
- Authoritative reference (사용자 지정): `docs/golden/{JavaParser_TodoController_ProgramSpec_Grayscale.xlsx,JavaParser_TodoService_ProgramSpec_Grayscale.xlsx,PMD_JSP_todo_ProgramSpec_Grayscale.xlsx,PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx}`. 이 경로의 최신 파일과 `docs/test/golden-manifest.sha256`이 검증 기준이다. 중복 복사본을 생성하는 경우 원본 및 해시 일치 여부를 반드시 검사한다.
- Create: `docs/test/golden-manifest.sha256`, `docs/test/fixtures-provenance.md`, `src/test/resources/fixtures/java/`, `src/test/resources/fixtures/jsp/`, `src/test/resources/fixtures/spring-mvc/`

**Interfaces**
- Consumes: 승인된 Spec 6, 7, 8, 13장; 사용자가 승인한 **실제 Excel 산출물**.
- Produces: Maven Java 17 컴파일·JUnit 테스트 실행, 고정 기준 파일과 검증 가능한 해시.

- [ ] **Step 1: 실패하는 `BootstrapTest.goldenFilesExistWithRecordedSha256()` 작성.** `docs/golden/` 4개 파일의 존재와 최신 `docs/test/golden-manifest.sha256` 해시 일치를 검사하고 누락/불일치 시 명시적 FAIL. 이전 ZIP 원본의 해시를 기준으로 삼지 않는다.
- [ ] **Step 2: MCSVR에서 `C:\dev\jdk-21\bin\java.exe -version` 및 `C:\dev\jdk-21\bin\javac.exe -version`으로 JDK 21 설치 확인 후, Maven 경로와 실행 가능 여부를 확인한다.** `JAVA_HOME=C:\dev\jdk-21`을 설정하고 `mvn -q -Dtest=BootstrapTest test` RED 확인. 제품 프로젝트 파일이 없어서 실패한 경우 테스트 실패와 환경 미설치를 구분해 기록한다.
- [ ] **Step 3: Maven 설정과 fixture 반입.** `docs/golden/`의 최신 Golden 4개 및 manifest 해시를 검증한다. 테스트 리소스로 복사할 경우 원본 보존 및 해시 동일성을 보장한다. `maven-compiler-plugin` `release=17`, `maven-surefire-plugin`, `exec-maven-plugin`, JUnit Jupiter, JavaParser `3.28.2`, PMD JSP `7.29.0`, POI HSSF/XSSF, Jackson JSON, SLF4J 로거를 **명시 버전 고정**. 분석기 2종의 API와 Java 17 호환성은 제품 빌드·테스트에서 직접 확인한다. 참고 fixture는 `SpringMvcStepByStep`의 고정 커밋 `ea85bf29e6ce4bdbc62eab06b97ceb66e6f5028f`에서 원문 확보 후 해시·라이선스 검토; 이 외 자작 미니 fixture는 별도 구분. Golden은 승인된 업로드 **실물**과 해시를 일치시켜서만 반입.
- [ ] **Step 4: `mvn -q -Dtest=BootstrapTest test` → PASS, `mvn -q -DskipTests package` → PASS 확인.** 기준 Excel을 확보할 수 없으면 RED 상태를 숨기지 말고 Golden 게이트를 BLOCKED로 기록하고 해당 기준본 테스트가 필요한 Task의 통과를 주장하지 않는다.
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

- [ ] **Step 1: `JobScannerTest` 작성.** `module1/controller/A.java` → `output/module1/controller/A.xls`, GUI 표시 모듈명 변경은 경로 불변, Java 모드 JSP 제외, JSP 모드 Java 제외, 상위+하위 등록은 같은 물리 소스 **각 모듈당 별개 작업**, 동일 폴더 경로 중복만 제거, 출력 루트의 소스 내부 위치 차단, symlink/junction 외부 추적 방지.
- [ ] **Step 2: `mvn -q -Dtest=JobScannerTest test` RED 확인.**
- [ ] **Step 3: `JobScanner.plan`과 `SourceJob` 구현.** 모듈 순서는 입력 List 순서 유지; 각 모듈 안에서는 상대 경로 **문자열 기준 안정 정렬**. `Files.walkFileTree` 기본 링크 비추적, 실제 경로의 선택 루트 이탈 차단. 출력 파일은 `sourceFile.getFileName()` 확장자만 교체. 출력 폴더 존재 여부만으로 차단하지 않는다.
- [ ] **Step 4: 해당 스캐너 테스트 PASS.** Windows 경로 대소문자·Junction 테스트는 Windows 실제 파일시스템에서 별도 검증하고 OS 권한 때문에 fixture 작성 불가 시 미검증으로 기록.
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

- [ ] **Step 1: `PoiWorkbookWriterTest` 대표 계약 테스트.** XLS·XLSX 각 1개 파일을 POI로 재열기, 1행/2행, 대표 헤더·D:F 회색·콘텐츠 테두리, 수식처럼 생긴 값의 STRING 유지, 기존 비대상 파일 보존·덮어쓰기 안전성을 핵심 사례로 검증한다. 셀 길이·행 한계·전 시트 스타일 전수 검사는 Task 12의 릴리스 검증으로 모은다.
- [ ] **Step 2: POI HSSF/XSSF 구현.** 단일 `WorkbookModel`→형식별 Workbook을 구성. 문자색·배경색 RGB 모두 `R=G=B` 회색조, 시트별 열 너비·정렬·글자줄바꿈·섹션 병합을 기준 샘플에 맞춘다. 부모 폴더 `createDirectories`, **출력 폴더 안의 임시 파일에 완전 저장 후 `REPLACE_EXISTING` 이동**(지원 시 atomic move)하여 기존 파일을 교체하며 실패한 임시 파일은 정리. 파일 쓰기 성공 시만 완료 처리. 소스 문자열을 수식 평가하지 않는다.
- [ ] **Step 3: 대상 테스트 PASS + 커밋.** `mvn -q -Dtest=PoiWorkbookWriterTest test` 한 번 실행하고 해당 Excel writer 변경만 확인한다. 커밋: 해당 경로만 `git add` → `git commit -m "feat: render monochrome XLS and XLSX workbooks"`.

## Task 08: Java 기본 9시트·정적초기화 포함 10시트 매퍼와 상세 사양

**Files**
- Create: `src/main/java/com/comus93/specgen/mapper/JavaSpecMapper.java`
- Test: `src/test/java/com/comus93/specgen/mapper/JavaSpecMapperTest.java`

**Interfaces**
- Consumes: Task 04·05 `JavaFacts`, Task 02 `JobConfig`, Task 03 `SourceJob`, Task 07 `WorkbookModel`.
- Produces: `JavaSpecMapper.map(JavaFacts,JobConfig,SourceJob) -> WorkbookModel`. 반환 시트의 정확한 순서는 Spec 7.2장.

- [ ] **Step 1: `JavaSpecMapperTest` 핵심 출력 테스트.** TodoController 9시트와 정적 초기화가 있는 TodoService 10시트, `04_처리단계`/`상세 사양` 분리, 표제부·상대경로 단일열·DETAIL 행번호/AST 미노출을 대표 값으로 확인한다. Golden 4건의 모든 셀·서식 비교는 Task 12에서 수행한다.
- [ ] **Step 2: Java 사실의 sheet row 변환 구현.** 소스 위치·AST는 **내부 순서/부모 관계 산정 전용**이며 출력 데이터에서는 삭제한다. `DETAIL (PSEUDO CODE)`는 AI 없는 **결정론적 토큰 정규화**(`INSTANTIATE→CREATE`, `ASSIGNMENT→SET`, `BRANCH→IF`)와 원문 표현식만 사용하고 `[Lxxx]`는 출력하지 않는다. 독립 `04_처리단계`를 `상세 사양`에 통합하지 않는다. 날짜: `authoringDateOverride != null`이면 그것, 그 외 `Files.getLastModifiedTime(source).toInstant()`을 실행 PC 로컬 timezone에서 날짜로 변환. 읽기 실패 시 해당 셀은 공란, **Task 10의 JobRunner가 같은 파일 메타데이터 오류를 파일별 WARNING으로 수집**. 실행 시 상대 원본경로는 선택한 모듈 폴더 기준. **Golden의 `src/main/...` 표기는 샘플 저장소 기준의 예시 경로이므로, 비교 시 상대 경로의 구조·형식과 입력 루트 차이를 구분한다.** `Import`는 원문 선언만 기록. Data가 없으면 설명 문구 대신 빈 행/공란.
- [ ] **Step 3: 대상 테스트 PASS + 커밋.** `mvn -q -Dtest=JavaSpecMapperTest test` 통과를 확인한다. 커밋: 해당 경로만 `git add` → `git commit -m "feat: map Java facts to design-oriented specification sheets"`.

## Task 09: JSP 5시트 매퍼와 링크 이벤트 명세

**Files**
- Create: `src/main/java/com/comus93/specgen/mapper/JspSpecMapper.java`
- Test: `src/test/java/com/comus93/specgen/mapper/JspSpecMapperTest.java`

**Interfaces**
- Consumes: Task 06 `JspFacts`, Task 02 `JobConfig`, Task 03 `SourceJob`, Task 07 `WorkbookModel`.
- Produces: `JspSpecMapper.map(JspFacts,JobConfig,SourceJob) -> WorkbookModel`.

- [ ] **Step 1: `JspSpecMapperTest` 의미 중심 테스트.** JSP 5개 시트의 이름/핵심 열·세 블록, `todo.jsp` 대표 입력·Submit과 `list-todos.jsp` 링크 3건을 확인한다. **LINK_CLICK은 표시 텍스트=Name, `href` 원문=설명, Return=공란**이어야 한다. 최종 화면 필드와 최소흐름에 필요한 정보가 Task 06 사실 모델에 없으면 누락을 숨기지 말고 Parser/Mapper를 좁게 보강한다. 전 시트/전 셀 Golden 일치는 Task 12에 맡긴다.
- [ ] **Step 2: JSP 사실의 workbook mapping 구현.** PMD JSP AST는 **내부 추출에만** 사용하고 Excel에는 노드 타입/깊이/소스행을 작성하지 않는다. `03_최소흐름`은 정적 태그·파일 구성 순서로 출력하며 런타임 흐름이라고 주장하지 않는다. 미확인 Object/Edit/다국어 ID는 공란, `href` 표현식 유지, 행사 전역변수 임의 생성 금지, 변경 사항 블록은 공란. JavaScript 코드가 없더라도 `<a href>`는 LINK_CLICK 출력.
- [ ] **Step 3: 대상 테스트 PASS + 커밋.** `mvn -q -Dtest=JspSpecMapperTest test`와 필요 시 영향을 받은 `PmdJspAnalyzerTest`만 실행한다. 커밋: 해당 경로만 `git add` → `git commit -m "feat: map JSP facts to five specification sheets"`.

## Task 10: 공통 JobRunner와 실제 CLI E2E

**Files**
- Create: `src/main/java/com/comus93/specgen/runner/{JobRunner,RunSummary,FileResult,ProgressEvent}.java`
- Create: `src/main/java/com/comus93/specgen/SpecGeneratorApp.java`
- Test: `src/test/java/com/comus93/specgen/runner/JobRunnerTest.java`
- Test: `src/test/java/com/comus93/specgen/cli/SpecGeneratorAppTest.java`

**Interfaces**
- Consumes: Task 02 `JobConfig`, Task 03 `JobScanner`, Task 04/06 analyzers, Task 08/09 mapper, Task 07 writer.
- Produces: `JobRunner.run(JobConfig, Consumer<ProgressEvent>) -> RunSummary`; `FileResult(sourceFile,outputFile,status,errorCode,message)` (status는 `FileStatus.SUCCESS|WARNING|FAILED`); `ProgressEvent(completed,total,sourceFile,status)`; 테스트 가능한 `SpecGeneratorApp.runCli(String[] args, PrintStream out, PrintStream err) -> int`. `main(String[])`의 `generate --config <json>` 지원을 구현하고 `gui` 분기는 Task 11에서 연결한다.

- [ ] **Step 1: `JobRunnerTest`/`SpecGeneratorAppTest` 핵심 흐름 테스트.** 대표 성공 파일과 실패 파일을 섞어 **파일별 실패 격리·계속 처리**, 모듈 순서와 같은 출력의 last-write-wins, 기존 다른 파일 보존, CLI 종료 코드 `0/1/2`를 검증한다. Task 03 경로 변형 전수 반복 및 실행 JAR/Java17/오프라인 E2E는 Task 12가 수행한다.
- [ ] **Step 2: JobRunner 구현.** `scan -> analyze -> map -> write` 파일마다 수행, 순차 처리, 파일별 실패 격리, 대상별 최종 경로/상태 요약. 작성일 파일 메타데이터를 못 읽는 경우 Excel 작성일은 공란이고 `FileResult.WARNING` 기록. `Consumer<ProgressEvent>`가 진행 UI/CLI에 알림. 로그는 상태·경로·간략 오류만 표시. CLI의 `--config`는 Task02 reader를 재사용하고 사용자 입력 외 데이터를 생성하지 않는다. `SpecGeneratorApp.main`은 CLI에 한해 `System.exit(runCli(...))`하며 GUI 진입점은 Task11에서 연결한다.
- [ ] **Step 3: 대상 테스트 PASS + 커밋.** `mvn -q -Dtest=JobRunnerTest,SpecGeneratorAppTest test`를 실행한다. 실제 CLI 1회 스모크는 JUnit으로 확인되지 않은 연결 문제가 있을 때만 추가한다. 커밋: 해당 경로와 `pom.xml`만 `git add` → `git commit -m "feat: run offline source jobs via CLI"`.

## Task 11: Swing GUI 폴더 선택·편집·진행 상태

**Files**
- Create: `src/main/java/com/comus93/specgen/gui/{GuiLauncher,SpecGeneratorFrame,ModuleTableModel}.java`
- Modify: `src/main/java/com/comus93/specgen/SpecGeneratorApp.java`
- Test: `src/test/java/com/comus93/specgen/gui/ModuleTableModelTest.java`

**Interfaces**
- Consumes: Task 02 `JobConfig`·`ModuleSelection`, Task 10 `JobRunner`·`ProgressEvent`.
- Produces: `GuiLauncher.launch() -> void` (Swing EDT에서 창 생성), `ModuleTableModel.addFolders(List<Path>)`, `ModuleTableModel.removeRow(int)`, `ModuleTableModel.updateSystem(String)`, `ModuleTableModel.toSelections() -> List<ModuleSelection>`.

- [ ] **Step 1: `ModuleTableModelTest` 핵심 상태 테스트.** 폴더 추가·동일 경로 중복 차단·상하위 폴더 독립 유지·편집한 서브시스템/모듈 값 보존·행 제거의 파일 미삭제를 대표 사례로 검증한다. 반복 수동 GUI 조작은 요구하지 않는다.
- [ ] **Step 2: Swing UI 구현.** 공통 설정과 Java/JSP 라디오, `+ 폴더 추가`(다중 디렉터리 선택), Explorer 폴더 DnD(`javaFileListFlavor`), 편집 가능한 Subsystem/Module 컬럼, 읽기 전용 절대 소스 경로, 제거 X, 출력 폴더 선택, 작성일 선택적 오버라이드. SwingWorker로 UI thread 정지 없이 JobRunner 실행; 진행·실패 요약. GUI 테마 픽셀/색상은 추가 확정사항이 아니므로 기능 중심; **Excel 무채색과 GUI 테마 구분**.
- [ ] **Step 3: 대상 테스트 PASS + 커밋.** `mvn -q -Dtest=ModuleTableModelTest test`를 확인한다. GUI 수동 스모크는 Task 12 통합 게이트에서 한 차례 수행하며 Task 11 완료 시에는 미실행을 명시한다. 커밋: GUI 파일·테스트·entrypoint만 `git add` → `git commit -m "feat: provide Swing job configuration interface"`.

## Task 12: 배포 패키지·의존성 보안·Golden 통합 검증

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

- [ ] **Step 1: 최종 인수/Golden 검증을 한곳에 구성.** `GoldenSampleTest`와 `scripts/verify-release.ps1`가 승인 Golden **실물과 SHA-256**, Java 9/10시트·JSP 5시트, 핵심 헤더/표제부·값·공란·D:F 회색·윤곽선 테두리, 행번호·AST/DETAIL 노출 금지, 상대경로 1열, 처리단계와 상세 사양 분리를 **대표 셀·경계 사례**로 판정하도록 작성한다. 전체 셀 픽셀/글꼴의 중복 전수 비교를 의무화하지 않지만 셀 길이·HSSF 행 제한·수식 주입 안전성은 대표 경계값으로 검증한다. 날짜는 오버라이드 또는 환경값으로 검증하고 기준 파일이 없으면 BLOCKED로 처리한다.
- [ ] **Step 2: Maven Shade(`minimizeJar=false`, 서비스 로더 리소스 유지)와 `jpackage --type app-image`를 설정.** JDK21 이미지에 자체 런타임 포함, GUI 런처와 별도 CLI 런처 생성. `dependency:tree`, 고정 의존성·라이선스·SBOM 및 `Get-FileHash` SHA-256을 `docs/security/dependency-inventory.md`에 기록. 전이 의존성·JRE17 classfile 호환성 검증 후 버전 최종 고정, 취약점 발견 시 계획서/스펙 변경관리.
- [ ] **Step 3: Task 12 최종 게이트 1회 실행.** `mvn -q clean verify`로 전체 회귀와 Golden 검증을 한 번 수행한다. 그 후 **실제 Java 17 JVM**에서 실행 JAR CLI, Windows GUI/CLI 런처, Java PATH 없이 구동, 네트워크 차단 오프라인 실행, 원본 소스 불변·동일 경로 덮어쓰기·비대상 파일 보존을 대표 입력으로 확인한다. 실패하면 **관련 변경 영향 테스트만 먼저 재실행**하고 최종 릴리스 게이트는 수정 후 다시 PASS가 확인되어야 완료한다. `docs/operations/README.md`에 실행 일자·버전·명령·PASS/FAIL/BLOCKED와 미검증 항목만 짧게 기록한다. 미검증 Golden/Java17/오프라인/배포 항목을 임의로 PASS 처리하지 않는다.
- [ ] **Step 4: 검증 후 변경 경로만 커밋.** `git diff --check`와 테스트 결과를 확인한 뒤 `git add pom.xml scripts docs/security docs/operations src/test/java/com/comus93/specgen/validation` (및 승인된 기준본만), `git commit -m "build: package and verify offline Java 17 generator"`. 필수 릴리스 검증 미통과 시 PASS나 배포 완료를 주장하지 않는다.

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
| 11 JRE17·런타임 동봉 배포 | 01, 12 |
| 12 파일 실패 격리·결정론·오류 | 03, 04, 06, 07, 10 |
| 13 AC-01 ~ AC-16 / Golden | 01~12, 종합 12 |
| 15 사용자 승인된 JS 제외·작성일·덮어쓰기·중첩 폴더 | 02, 03, 06, 08, 09, 10, 12 |

## Paseo Review / Delegation Handoff (계획 승인 **후**)

**호출 정책 (다른 프로젝트에도 적용할 Codex 전역 규칙은 MCSVR `C:\Users\comus\.codex\AGENTS.md` 참조):** Orchestrator가 MCSVR 내부에서 실행 중이면 로컬 파일·Git·JDK/Maven·터미널 작업은 기본 Shell로 수행한다. 같은 머신의 터미널을 쓰기 위해 `Paseo-MCSVR` 플러그인을 경유하지 않는다. Paseo API는 실제 하위 에이전트 위임·상태 관리 등 Paseo 고유 작업에 필요한 경우에만 사용한다.

**2026-11-05 별도 플랫폼 문제:** Paseo `list_workspaces` 정상, 관리형 `create_terminal`은 `Terminal worker is not running`으로 실패(기존 Orchestrator 로그에서는 `Transport closed`). 이는 Golden Sample 부재와 별개이며, 관리형 터미널 worker가 정상화되었다는 증거 없이는 해당 기능을 정상으로 간주하지 않는다. 로컬 Shell로 가능한 업무는 플러그인 없이 실행하고, Paseo 고유 에이전트 위임 경로는 위임 전에 별도 상태 확인한다.

1. **Paseo 투입 자료**: 최신 Spec 경로와 해시, 이 Implementation Plan 경로와 해시, MCSVR `docs/golden/` Excel 4종 및 `docs/test/golden-manifest.sha256`, 테스트 fixture 출처, `main` 기준 commit. 과거 ZIP 버전으로 대체하지 않는다.
2. **1차 단계는 리뷰**: Paseo가 (a) 최신 요구사항 전부가 Task/AC에 매핑됐는지, (b) 작업 순서·인터페이스와 의존성이 실행 가능한지, (c) 보안/오프라인 경계, (d) 최신 Golden 기준본·Manifest 정합성, (e) Java17·JSP 레거시 호환성을 확인한다. **리뷰는 문서 이해가 목적이며 스펙·계획을 임의 수정하지 않는다.**
3. **리뷰 결과 보고**: 오류/모호성은 구체적인 스펙·Task ID와 수정안으로 사용자에게 보고. 승인된 요구사항 자체를 일방 변경하지 않는다. 계획 변경이 필요하면 검토받고 plan revision을 기록한다.
4. **개발 위임**: 사용자 지시가 있는 Task만 실행한다. **완료된 Task 01~06은 기존 이력 유지, 잔여 Task 07~11은 위 경량 3단계 게이트**로 수행한다. RED/대상 PASS를 위한 의미 없는 중복 Maven 호출과 Task별 재계획·재리뷰를 강제하지 않는다. 무단 차기 Task 전개는 금지하고 동일 파일에 여러 에이전트가 동시에 쓰지 않는다.
5. **검증 독립성**: Task 07~11에는 각 Task의 대상 테스트 PASS/FAIL/BLOCKED와 커밋만 간단히 받는다. 별도 검증 에이전트는 **사용자 요청 또는 Task 12 최종 통합 검증 시 필요할 때** 투입한다. 임의 중복 소스 제거·JavaScript 함수 분석 추가·출력 충돌 차단 같은 승인되지 않은 편의 기능은 반려한다.
6. **마무리**: Task 07~11은 변경 영향 테스트만, **전체 회귀·Golden·Java17·오프라인·배포 검증은 Task 12**에서 집중한다. 브랜치 병합·배포는 사용자 명시적 지시 및 최종 검증 결과에 따른다.

## Planning Self-Review / Approval Gate

- **범위**: 단일 제품 코어+두 독립 분석 경로+두 실행 표면. Java 및 JSP는 개별 테스트로 검증하고 단일 공유 출력·실행 코어로 통합한다. 이 범위를 여러 중복 스펙으로 재작성하지 않는다.
- **계약**: Task 02 `JobConfig`, Task 03 `SourceJob`, Task 04/06 `SourceFacts`, Task 07 `WorkbookModel`, Task 08/09 `SpecMapper`, Task 10 `JobRunner`만 경계를 넘는다.
- **Golden status**: MCSVR `docs/golden/` Excel 4종과 새 manifest를 최신 스펙 기준으로 관리한다. Golden 미확보 블로커는 해소됐다. 개발/런타임/배포 검증이 완료되었다는 뜻은 아니다. **이전 계획서의 정확히 Java 9시트 제약은 정적 초기화 시 10시트 예외로 수정**했다.
- **보안**: 소스 컴파일·동적 실행·외부 프로세스·런타임 다운로드·AI 전송은 금지.
- **계획 사용자 승인 완료(2026-11-05)**: 개발 머신은 MCSVR, JDK는 `C:\dev\jdk-21\bin`. **Paseo 리뷰 및 구현은 사용자의 후속 실행 지시를 받은 후에만 진행**한다.
- **2026-11-06 사용자 경량화 개정**: 승인 Spec/Golden/보안 요구를 바꾸지 않고 Task 07~11 체크리스트를 각 3단계로, Task 12 최종 검증을 4단계로 간소화했다. 이미 완료된 Task 01~06의 이력을 소급하지 않으며 형식적 RED, 매 Task 전체 테스트, 중복 리뷰 및 검증 보고서를 제거한다.
