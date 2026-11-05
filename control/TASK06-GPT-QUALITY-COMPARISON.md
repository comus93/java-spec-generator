# Task 06 독립 코드 품질 검토: GPT-6 재측정 vs GPT-6 Instant

- 검토일: 2026-11-06 (KST)
- 검토 목적: 속도 순위가 아니라 **현재 구현을 실제 프로그램 사양서 생성기에 재사용할 수 있는지** 판단한다.
- 공통 기준: `108de803e15aaa5467f4a163f6197ad6c12fb445`, `control/TASK-06-BRIEF.md`, 승인된 `docs/golden/PMD_JSP_*.xlsx`
- 후보 A: `bench/task06/dev-agentdock-retest` / `dd283034a18948b974ff7a6a175eaa84fc1623c4`
- 후보 B: `bench/task06/dev-agentdock-instant` / `2a158d582149b0d6346d157d2199bb9812dbc33b`
- 검사 방식: Java 소스 및 자체 테스트 정적 검토 + 동일 입력을 대상으로 각각의 **컴파일된 Parser를 별도 임시 Java Probe로 실행** + 승인된 Golden Excel에서 기대 값 확인.
- 변경 원칙: 개발 후보 브랜치의 소스·테스트·커밋은 수정하지 않았다. 검증용 `Probe.java` 및 추가 fixture는 임시 디렉터리에서 실행했다.
- 범위: Task 06 파서만 비교. Task 09 매퍼나 전체 엑셀 출력 파이프라인의 통합 실행을 검증한 것은 **아니다**.

## 핵심 판정

| | GPT-6 직접 실행(재측정) | GPT-6 Instant + AgentDock |
| --- | --- | --- |
| **현재 실용 수준** | **확장 가능한 부분 구현 / 프로토타입** | **기술 검증용 최소 구현 / PoC** |
| Task06 자체 테스트 | 4 PASS; 전체 49 PASS | 2 PASS; 전체 47 PASS |
| 기본 JSP 폼 필드/링크 | 동작 | 동작 |
| 부모 폼의 Object 관계 | 유지함 | 누락 |
| include 파일 경로 | 유지함 | 누락 |
| 이벤트 Description/Return 의미 | URL을 Description에 저장, Return은 `null`. 단 `href=` 인용부호 원문은 미보존 | 설명에 링크 문자, Return에 URL 저장: **의미상 매핑 오류** |
| 중첩 태그 링크 표시 문자열 | 동작 | 공백 |
| 읽기/파싱 오류 | 파일별 구분 | 파일별 구분 |
| 현재 제품 투입 | **불가**, 결손 기능 보완 후 재평가 | **불가**, 핵심 매핑/추출 보완이 먼저 |
| 추천 용도 | 다음 구현의 **기반 후보** | PMD 사용 가능성 증명, 간단 구조 추출 |

**판정 근거:** 두 구현의 테스트 통과 자체는 사실이지만, 테스트 세트가 서로 다르고 필요한 제품 사실 전체를 검증하지 않는다. 따라서 테스트 통과 개수나 개발 줄 수를 품질 점수로 바꾸지 않는다.

## 독립 실행 결과

실행 입력은 각각의 기존 `todo.jsp`, `list-todos.jsp`와 공통 임시 입력 2개다. 임시 입력은 `<%@ include file="partials/header.jspf" %>`, `<form:form commandName="invoice">` 하위의 `<form:input path="amount" readonly="true" />`, `<a href="/view"><strong>View details</strong></a>`, `onclick`만 있는 링크, 의도적으로 닫지 않은 `<c:forEach`를 포함한다.

| 항목 | 재측정 코드 A | Instant 코드 B | 해석 |
| --- | --- | --- | --- |
| `todo.jsp` 화면 필드 수 | 3 | 3 | Golden은 4행(Submit 포함). 둘 다 화면 필드 포괄성이 부족함 |
| `todo.jsp` 필드 `object` | `todo` | `null` | B는 부모 `form:form` 관계를 읽지 못함 |
| `todo.jsp` `id` editable | `false` | `null` | B는 hidden에 대한 결정 가능한 정보도 버림 |
| `list-todos.jsp` 화면 필드 수 | 0 | 0 | Golden은 7행. **최종 사양서 관점의 공백**; Task09가 flow를 재가공할 가능성과 구분해야 함 |
| `list-todos.jsp` flow 수 | 31 | 31 | Golden 최소 흐름 11행과 불일치하지만 **Task09의 정적 흐름 필터링 전**이므로 수 자체를 Task06 오류로 단정하지 않음 |
| Update/Delete/Add 링크 이벤트 | 3/3 생성 | 3/3 생성 | 링크 감지 기능 자체는 둘 다 통과 |
| Update 설명 | `/update-todo?id=${todo.id}` | `Update` | A가 의미상 올바른 필드. A도 `href="..."` 리터럴은 미보존 |
| Update Return | `null` | `/update-todo?id=${todo.id}` | B는 Return 오염 |
| JSP include 경로 | `common/header.jspf` 등 | `include` + 빈 속성 | B는 경로 손실 |
| `commandName=invoice` Object | `invoice` | `null` | A는 폼 소유자 복원 가능 |
| 중첩 `<strong>` 표시 텍스트 | `View details` | 공백 | B의 `collectText`는 자식 요소 재귀 탐색 누락 |
| `readonly=true` editable | `true` | `null` | A도 readonly를 제대로 반영하지 못함 |
| malformed JSP | `PARSE_ERROR` | `PARSE_ERROR` | 둘 다 파싱 실패 감지 |
| `spring:message` constants | 0 | 0 | 국제화/상수 관련 사실 모델을 확장할 필요가 있음 |

### 필드 매핑 증거

재측정 코드 (`PmdJspAnalyzer.java`, 약 85~87행):
```text
EVENT Update | description=/update-todo?id=${todo.id} | return=null
```

Instant 코드 (`PmdJspAnalyzer.java`, 약 65~67행):
```text
EVENT Update | description=Update | return=/update-todo?id=${todo.id}
```

승인 Golden의 LINK_CLICK 행은 `Type=LINK_CLICK`, `Name=Update`, `설명=href="/update-todo?id=${todo.id}"`, `Return=공란`이다. Instant 자체 테스트는 오히려 링크 URL을 `returnValue`로 단정해서 검사하므로 이 오류를 발견하지 못한다.

### 코드 구조·유지보수성

- **A**: `LinkedHashMap` 복사로 속성 순서를 안정적으로 보존, 중첩 폼 탐색/속성 파싱/링크 표시 텍스트 등의 책임을 별도 메서드로 분리, custom/오류 테스트 4개. 하지만 `constants`는 항상 빈 목록이고, `editable=!hidden`으로 readonly/disabled를 제대로 처리하지 못한다. 입력 속성을 맵 값으로 저장해서 원문의 따옴표·형식까지 그대로 유지하는 건 아니다.
- **B**: 110줄 파서와 35줄 테스트로 PMD 연결·기초 AST 순회는 간결하게 구현했다. 그러나 `object=attrs.get("commandName")`을 **필드 자신의 속성**에서 조회해 상위 폼 정보를 놓치고, `Map.copyOf`로 속성 순서를 보존하지 않으며, include path와 중첩 링크 텍스트를 유실한다. `editable=null`을 고정하여 hidden 필드의 의미도 보존하지 않는다.
- **공통**: PMD 7.29 `JspLanguageModule`, Registry, `TextDocument`, `ParserTask`, Visitor를 사용하고, 읽기/파싱 실패를 `SourceAnalysisException`으로 변환한다. 예외 `location`은 실제 출력에서 여러 줄의 PMD 원문 메시지가 나타났으므로 사용자에게 제공할 간결한 위치 정보로 정규화할 필요가 있다.

## 요구사항 충족과 제품 사용 가능성을 구분해야 하는 이유

Task 06의 명시적 개발 테스트는 주로 `todo.jsp`의 폼 입력과 `list-todos.jsp`의 LINK_CLICK 3개를 다룬다. 반면 **실제 Golden Excel**에는 표시 라벨, EL 바인딩, 링크와 Submit을 포함한 화면 필드가 더 많이 존재한다. 후속 Task 09의 JSP 매퍼는 이 사실들을 받아서 최종 5시트를 만들어야 하므로, 최종 제품의 요구 수준을 만족하려면 추가 추출/매핑이 필요하다.

다시 말해:
- A와 B는 **기초 Parser가 동작한다**는 점은 입증했다.
- A가 현 단계에서 **더 풍부하고 의미가 정확한 사실**을 제공한다는 점도 검증됐다.
- 하지만 **어느 쪽도 Golden 일치의 전체 사양서 생성 준비가 끝난 것은 아니다**.
- **flow 31개 vs Golden 11개**는 Task 09 매퍼의 투영 과정이 남아 있으므로 *그 자체만으로 결함*이라고 판단하지 않는다.
- 모든 코드 품질 판단은 2026-11-06 검증한 2개 브랜치에만 적용한다. Terra/DeepSeek 구현 품질은 이 보고서에서 검증하지 않았다.

## 우선 보완 순서

1. **필수 P0, 두 후보 공통:** `list-todos.jsp`의 표시 필드/라벨/EL 및 `todo.jsp` Submit을 최종 Golden으로 매핑할 수 있는 충분한 사실 추출. `spring:message` 코드/다국어 ID 취급 확인.
2. **필수 P0, B 추가:** 이벤트 `description/returnValue` 의미 수정, 폼 Object 상속, include 경로, 중첩 링크 표시 텍스트.
3. **P1, A 추가:** `readonly`/disabled/editable의 명시 여부 및 추론 규칙 정립, raw `href` 및 태그 속성의 원문 표현 보존.
4. **P1, 공통:** 비표준 JSP, 멀티라인 속성, 중첩 EL, 구문 오류, JSP 파일별 실패 격리를 **고정된 공통 인수 테스트**로 검증.
5. **독립 QA:** 개발자 자신이 작성한 테스트와 별도로 Golden 기준 테스트를 실행하고, 완료 시간을 실제 검증 통과까지 포함해 다시 비교.

## 속도·품질 종합 해석

- 개발→커밋: **A 8분 47초(개발 시작 후), B 3분 33.716초(개발 시작 후)**.
- 완료 관측: **A 9분 11.116초, B 4분 12.114초**. 두 실행 모두 환경 준비를 타이머 밖에서 끝냈지만 내부 맥락·자체 테스트 수·구현량이 달라 정밀한 모델 전용 비교는 아니다.
- 개발량: **A +402줄/4파일, B +174줄/3파일**. 코드가 짧은 것은 B의 유용한 특성이지만, 일부 기능 누락·값 손실이 동반되므로 *구현 효율*과 *제품 완성도*를 분리해서 봐야 한다.
- **현재 재사용 권고:** A를 기능 확장의 기반 후보로 삼는 편이 합리적이다. B는 빠른 PoC·API 연결 확인에는 가치가 있지만, 이벤트·폼·include 의미를 고친 후에야 제품 단계로 승격할 수 있다.
- **신뢰 한계:** 이번 검증은 4종 입력과 코드 리뷰에 기반한 제한적 독립 스모크/인수 검증이다. 모든 JSP 변종, 대용량 성능, 타 Task 연계, 보안 속성, Golden 실제 생성에 대한 완전한 품질 인증은 아니다.

## 원본 및 재현 위치

- 원시 소요 시간·상태: `control/METRICS.csv`
- 통합 비교: `control/TASK06-BENCHMARK-RESULTS.md`
- A 자체 개발 기록: `control/AGENTDOCK-RETEST-RESULTS.md`
- B 자체 개발 기록: `control/AGENTDOCK-INSTANT-RESULTS.md`
- 기준 요구사항: `control/TASK-06-BRIEF.md`
- 승인 Golden: `docs/golden/PMD_JSP_todo_ProgramSpec_Grayscale.xlsx`, `PMD_JSP_list-todos_ProgramSpec_Grayscale.xlsx`
- 독립 실행: 두 브랜치의 `target/classes`를 사용하여 `C:\Users\comus\.agentdock\tmp\task06-independent-review-20261106\Probe.java`에서 각자 같은 fixture를 파싱했다. 이 파일은 검토 시점의 임시 증거로, 영구 저장소에 들어간 파일이 아니다.
