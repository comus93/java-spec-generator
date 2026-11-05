# Task 06 독립 품질 비교 재현 가이드

이 폴더는 **GPT-6 직접 재측정**(`dd28303`)과 **GPT-6 Instant**(`2a158d5`)의 **기존 컴파일 결과를 읽기만 하는** 별도 검증 스크립트를 보관한다.

- `Task06QualityProbe.java`: 두 후보에 동일한 JSP fixture를 주고 화면 필드, 이벤트, 흐름, 오류를 출력한다. 테스트용 JSP 2개는 Java의 시스템 임시 폴더에 생성한다.
- `run-quality-probe.ps1`: MCSVR JDK 21/Maven 3.9.16과 각 브랜치의 `target/classes`로 probe를 각각 실행한다.
- `2026-11-06-compare-output.txt`: 독립 실행 결과의 실제 출력 기록.

## MCSVR에서 재실행

PowerShell:

```powershell
& 'C:\Workspace\github\java-spec-generator-bench\results-publish\control\quality-probe\run-quality-probe.ps1'
```

필요한 사전 조건은 개발 워크트리가 위 경로에 있고, 각 워크트리에 해당 커밋의 `target/classes`가 이미 빌드되어 있다는 것이다. Maven 의존성 클래스패스는 매 실행 다시 계산한다.

### 검증 범위와 해석 한계

`todo.jsp` / `list-todos.jsp`, 그리고 include·폼·readonly·중첩 링크·onclick·malformed JSP를 포함한 **동일 입력 4개**를 검사한다. 이 테스트는 독립적인 검증 보조 자료지만 완전한 수용 시험은 아니다. Golden Excel의 실제 생성·매퍼 검증은 별도 Task 09/12 검증 범위다.

검증과 품질 판정은 상위 문서 `control/TASK06-GPT-QUALITY-COMPARISON.md`를 참조한다.
