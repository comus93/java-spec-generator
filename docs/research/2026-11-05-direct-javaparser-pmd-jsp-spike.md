# JavaParser + PMD JSP Parser 직접 연동 스파이크 (2026-11-05)

## 결정

- 사양서 생성기의 구현 언어는 **Java**.
- Java 분석: `com.github.javaparser:javaparser-core:3.28.2` 라이브러리 직접 호출.
- JSP 분석: `net.sourceforge.pmd:pmd-jsp:7.29.0` 라이브러리 직접 호출.
- **LeflectJava CLI 및 Node.js 분석 프로세스는 사용하지 않는다.**
- GUI는 Swing 기반 데스크톱 UI, CLI는 같은 Java 분석/생성 코어를 공유한다.
- Java/JSP 분석은 서로 독립 실행하며, Controller–JSP 매칭을 하지 않는다.
- 최종 사용자 환경은 JRE 실행형을 기본으로 하고 런타임 포함 단독 배포를 대안으로 제공한다.

## 목적과 보안 경계

외부 블랙박스 실행기에 고객사 Java/JSP 소스를 넘기지 않는다. 승인된 폴더의 소스를 Java 애플리케이션이 **읽기 전용**으로 읽어 정적 AST만 생성한다. 소스를 실행/컴파일하거나 네트워크로 전송하지 않으며, 런타임 의존성 다운로드는 금지한다.

라이브러리 소스 공개만으로 안전성을 보장할 수 없으므로 빌드에서 의존성 버전을 고정하고, 하위 의존성 및 라이선스 검토, 배포 JAR 해시 관리, 오프라인 작동 검증을 수행한다.

## 실제 검증

환경: MCSVR, Eclipse Temurin JDK 21.0.13. 검증은 `.local/parser-poc/`에서 **JavaParser 및 PMD를 JVM 라이브러리 API로 직접 호출**하여 진행했다. Maven은 실험용 의존성 준비에만 사용되었다.

참조 소스: `.local/SpringMvcStepByStep/src/main/` (기존 LeflectJava 스파이크와 동일한 Spring MVC 샘플).

| 파일 | 결과 | 확인 값 |
| --- | --- | --- |
| `TodoController.java` | PASS | JavaParser MethodDeclaration 9개 |
| `list-todos.jsp` | PASS | AST 노드 107개, attribute 13개, EL 6개 |
| `todo.jsp` | PASS | AST 노드 103개, attribute 23개 |
| `welcome.jsp` | PASS | AST 노드 30개 |
| `error.jsp` | PASS | AST 노드 15개 |
| `error-specific.jsp` | PASS | AST 노드 15개 |
| 임의 생성 `non-xhtml.jsp` | PASS | 비종료 `input`/`br`, 따옴표 없는 속성이 포함된 최소 사례에서도 파싱 성공 |

`list-todos.jsp`에서 `c:forEach`의 `items=${todos}`, `var=todo`와 아래 링크 3개를 실제로 추출했다.

- `href="/update-todo?id=${todo.id}"` → `LINK_CLICK` 후보 `Update`
- `href="/delete-todo?id=${todo.id}"` → `LINK_CLICK` 후보 `Delete`
- `href="/add-todo"` → `LINK_CLICK` 후보 `Add`

`todo.jsp`에서는 `form:form`, `form:input`, `form:hidden`, `form:label`, `form:errors`와 각 태그의 `path`, `type`, `required` 등의 속성을 추출했다.

참고: 위 측정치는 PMD AST/JavaParser 자체의 파싱 결과이다. 최종 Excel 사양서 생성 기능이나 전체 처리단계 Visitor는 아직 구현·검증하지 않았다.

## 출력 정책 유지

- 시스템은 사용자 전역 입력.
- 서브시스템은 선택한 모듈 폴더별 입력이며 기본값은 시스템명.
- 모듈은 사용자가 선택한 최상위 폴더명.
- Java와 JSP는 별도 모드로 실행하고 선택한 각 모듈 폴더 안의 해당 확장자만 재귀 탐색한다.
- 원본 1개당 동일 파일명 `.xls` 사양서를 생성한다. 동명 충돌을 피하기 위해 출력 하위 경로를 보존한다.
- JavaParser AST에서 Java 클래스·필드·메서드·파라미터·제어문·호출을 추출한다. LeflectJava `orderedSteps`와 동일한 수준을 목표로 하는 별도 Visitor 구현이 필요하다.
- PMD JSP AST에서 화면 필드, 태그, EL 원문, JSP include 원문, `<a href>` LINK_CLICK 이벤트를 추출한다.
- 데이터에 없는 설명, 비즈니스 의미, PSEUDO CODE 문구는 추론하지 않고 공란으로 남긴다.
- 프로그램/변경 정보 등 미입력 항목은 공란 유지.

## 아직 남은 리스크

1. PMD 공식 문서는 XHTML 준수 JSP 중심 지원과 EL/스크립틀릿 내부 분석의 한계를 설명한다. 위 최소 비표준 JSP가 통과했다고 일반적인 비표준 JSP 전체가 지원되는 것은 아니다.
2. JSP 내부 JavaScript 함수/이벤트를 PMD JSP AST만으로 구문 수준 분석할 수 있는지는 미검증. 필요 시 JS 전용 오픈소스 라이브러리를 **직접 연동**하거나 제한된 범위를 원문으로만 기록한다.
3. JRE 최소 버전은 **검증 전**이다. 실험한 핵심 클래스는 Java 8 classfile(major 52)이지만, 전체 전이 의존성 및 배포물은 별도 호환성 검증 필요.
4. 오프라인 실행기, Swing GUI, Apache POI 기반 Excel 생성, 코드 처리단계 Visitor, 레거시 JSP 오류 복구는 아직 구현되지 않았다.
5. SLF4J binding 미포함으로 실험 로그에 NOP 경고가 출력되었다. 제품 배포 시 로그 설정을 고정해야 한다.

## 다음 단계

1. 분석 API의 자료형과 소스 라인 규약을 정의하고 기존 Java/JSP 사양서 시트 열에 맞춰 매핑한다.
2. JavaParser AST Visitor로 Java 처리단계 및 메서드 정의를 추출한다. 소스 내용과 라인에 근거한 데이터만 작성한다.
3. PMD JSP AST Visitor로 화면 필드, 링크 이벤트, 태그/속성, 원본 경로를 추출한다. 레거시 JSP 문법 사례를 추가한다.
4. Apache POI로 `.xls` 생성 후 기준 샘플 파일과 검증한다.
5. CLI와 Swing GUI에 공통 코어를 연결하고 오프라인/JRE 전용 패키지 검증을 수행한다.

관련 문서: `docs/research/2026-11-04-leflectjava-spike.md` (과거 비교 실험으로만 유지).
