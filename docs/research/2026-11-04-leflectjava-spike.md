# LeflectJava 1차 실증 결과 (2026-11-04)

> 상태: 탐색용 Spike 완료. 본 문서는 설계 승인 또는 제품 구현 완료를 의미하지 않음.

## 결정 및 목표

- 우선 추출 엔진: [LeflectJava](https://github.com/zerotosetta/leflect-java) (MIT).
- 대안 검토는 실제 추출 실패 또는 유지보수 문제 발생 시: JavaParser + PMD JSP, 필요 시 Jasper/Eclipse WTP.
- 문서 출력 후보: [poi-tl](https://github.com/Sayi/poi-tl) (Apache-2.0). **본 실증에서는 Word 생성 미실행**.
- AI 및 외부 추론 서비스 미사용. 회사망 내 독립 실행을 최종 목표로 하되, 오프라인 패키징은 미검증.
- 소스 파일 1개마다 프로그램 사양서 1개 생성.
- Java 사양: 표제부, OBJ 목록, OBJECT 선언, 메서드 정의.
- JSP 사양: 표제부, 파일 참조 목록, Controller 호출 관계 최소 흐름도, 화면 필드 정의(필드명, Object, 참조 필드, Edit Y/N, Remark, 항목 유형, 다국어 ID).
- 표제부는 시스템명/서브시스템명/작성자/작성일/개발유형/프로그램 개요를 포함. 소스에 없는 값은 설정 또는 사용자 입력으로 처리하고 임의 추정 금지.

## 실증 환경

- 실행 PC: BEDROOM (Windows 11).
- 실행기: Node.js 24.19.0, corepack pnpm 9.0.0, JDK 21.0.6.
- Java Worker 빌드: Apache Maven 3.9.9, `mvn -DskipTests package`.
- 대상 레포: [in28minutes/SpringMvcStepByStep](https://github.com/in28minutes/SpringMvcStepByStep) (Spring MVC 4.2 계열의 교육용 Java/JSP 샘플).
- 탐색용 임시 경로: `%TEMP%\leflectjava-spike-20261104`. 제품 레포와 기존 업무 프로젝트를 변경하지 않음.
- LeflectJava 원본 소스는 2026-11-04 시점 default branch를 clone하여 사용. 별도 버전 핀ning/재현성 설정 필요.

## 확인된 실제 결과

### 1차 (JSP lightweight, Java Worker 미연결)

- 소스 스캔 84개, JSP 5개 파싱 성공, Taglib 4개 식별.
- Java 상세 메서드 AST는 worker JAR 부재로 skip.
- JSP ↔ Java 참조의 일부는 **해결되지 않은 태그 핸들러 참조**로 기록. 이를 Controller 호출 성공으로 해석하지 말 것.

### 2차 (Java Worker JAR 연결, JSP lightweight)

- 스캔 85개: Spike가 샘플 폴더에 생성한 `leflect.config.json` 포함.
- Java 소스 **8개** 파싱, **메서드 36개** 추출.
- JSP **5개** 파싱, Taglib **4개** 식별.
- Java 메서드/호출 관계 edge **67개**.
- JSP-Java edge **10개**, 하지만 해당 샘플의 `form:*` / `spring:message` 등 태그 참조가 해결되지 않았고 실제 Controller route 연결과는 다름.
- 전체 미해결 edge **41개** (외부 라이브러리 등 참조 포함).
- 8개 클래스, Controller 5개, Service 1개 등 레이블 분류.
- Java 메서드 세부 구조 `orderedSteps`에 분기, 반환, 필드 읽기, 객체 생성, 호출, 원본 행 번호 및 `branchPath`가 포함됨.
- 실제 예: `TodoController.addTodo(ModelMap,Todo,BindingResult)`의 `result.hasErrors()`, `TodoService.addTodo(...)`, `model.clear()`, `return "redirect:list-todos"` 호출/반환 순서 확인.
- Java OBJECT 예: `TodoController`에 `logger: Log`, `service: TodoService` 필드 메타데이터 확인.

### JSP 화면 필드 근거

대상: `src/main/webapp/WEB-INF/views/todo.jsp` 및 `list-todos.jsp`

- `form:form commandName="todo" method="post"` 파악.
- `form:hidden path="id"` 파악.
- `form:input path="desc" type="text" required="required"` 파악.
- `form:input path="targetDate" type="text" required="required"` 파악.
- `input type="submit" value="Submit"` 파악.
- `list-todos.jsp`의 `td`, `a href`, `c:forEach items="${todos}" var="todo"`, `spring:message code="todo.caption"` 파악.
- 모든 태그에는 소스 위치(lineRange)가 존재. 이는 **원본 추출 결과**이며 화면 명세서의 Object, Edit Y/N 등의 최종 표출값은 별도 변환 규칙이 필요함.

## 미완료 및 리스크

1. **JSP ↔ Controller URL 매핑 미완료**: `href`/`form action`과 Spring MVC `@RequestMapping`을 매칭하는 전용 분석 계층 필요. 현재 나온 JSP-Java edge 10개를 Controller 경로로 오해하지 말 것.
2. **Taglib/TLD 해석 미완료**: 현재 샘플 TLD 레지스트리는 빈 배열. `form:*` 및 `spring:message`와 같은 외부 라이브러리의 태그 의미를 보완할 설정/매핑 규칙 필요.
3. **JSP의 최종 필드 명세 미완료**: Object 추적, 필드명-라벨 연결, Edit 판정, Remark 규칙, 다국어 리소스 매핑 개발 필요. 동적 여부는 '미확정'으로 표시.
4. **파일 목록 확장 필요**: `.jspf` include 등 참조 파일과 프로젝트 내 실제 파일 존재 여부를 별도로 검증해야 함.
5. **표제부 자동 채움 제약**: 작성자/작성일/업무 개요 등은 소스에 값이 없으면 설정값 또는 직접 입력.
6. **Jasper-mode / classpath 자동 발견 미검증**: 이번 실증은 `lightweight` 사용. 복잡한 사내 JSP에 대해 추가 실증 필요.
7. **poi-tl 출력 미검증**: Word 템플릿, 표제부, 파일별 1:1 출력은 후속 단계.
8. **운영 배포 미검증**: Node + Java + Maven을 운영망에서 요구하지 않는 번들 형태와 라이선스 고지, 오프라인 실행 검증 필요.
9. **테스트 샘플 편향**: 이번 결과는 공개 Spring 4 교육용 샘플에만 한정되며 사내 커스텀 태그 및 혼합 스크립틀릿 품질을 보증하지 않음.

## 다음 검증 순서 (Superpowers 설계 승인 전)

1. JSP 화면 필드 정의서 변환 규칙 설계: 태그/속성/EL → 필드명·Object·참조필드·Edit·유형·다국어 ID, 원본 근거 위치 보존.
2. Java OBJ 목록·OBJECT 선언·메서드 정의의 출력 모델 설계.
3. Spring 4 Controller 호출 관계의 URL/HTTP method/뷰 이름 매핑 범위와 '미해결' 출력 규칙 정하기.
4. Word `poi-tl` 템플릿 표제부와 샘플 결과물 설계.
5. 사용자 승인 후 `docs/superpowers/specs` 디자인 스펙 → Implementation Plan → Task별 구현/변경 영향 검증.
6. 실패 사례가 구체적으로 확인될 때에만 JavaParser+PMD JSP/Jasper 대안 평가.

## 재현용 핵심 명령

```powershell
# LeflectJava 루트에서, 대상 repo는 별도 위치에 clone 되어 있다고 가정
corepack pnpm install --frozen-lockfile
# 일부 빌드 스크립트는 pnpm이 PATH에 있어야 하므로 shim 또는 별도 경로 구성 필요
# Maven/JDK 사용 가능 시 java-worker/pom.xml을 package하여 JAR 생성
$env:LEFLECT_JAVA_WORKER_JAR="<absolute-path-to-java-worker-jar>"
node bin/leflect init --root "<spring4-repo>" --config-format json --yes
node bin/leflect analyze --root "<spring4-repo>" --out "<analysis-dir>" --jsp-ast-mode lightweight --format json
```

분석 결과 근거 파일: `analysis/index/java-files.json`, `analysis/index/java/**/*.json`, `analysis/index/jsp-files.json`, `analysis/jsp-meta/**/*.json`, `analysis/graph/**/*.json`, `analysis/report/summary.json`.
