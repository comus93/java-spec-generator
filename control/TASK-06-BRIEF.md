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
