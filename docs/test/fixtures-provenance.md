# Test fixture provenance

## Upstream reference sample

- Repository: `https://github.com/in28minutes/SpringMvcStepByStep`
- Pinned commit: `ea85bf29e6ce4bdbc62eab06b97ceb66e6f5028f`
- Commit verification date: 2026-11-05
- License: MIT (`LICENSE`, upstream SHA-256 `d4af2b5f03529f190721be7e65f8f34b511a31f2ab47f25b39192515629dc520`)
- All files below are verbatim copies of files from that commit. No content or line endings were changed.

## Upstream files and recorded SHA-256

| Upstream path | SHA-256 |
|---|---|
| `src/main/java/com/in28minutes/todo/TodoController.java` | `d0fccd9d5586e6fa65fc8bf2a6dabdf028e082e371983915ddcff96df032d9ba` |
| `src/main/java/com/in28minutes/todo/TodoService.java` | `78812c50f8e1b205b71d195f2f802f56a80f0aa74a015ab7c12fdc49bcf4ff8c` |
| `src/main/webapp/WEB-INF/views/todo.jsp` | `e1acdf0e214ce3997be75d48708897d3a4a120364400c814f5d75c969a7f0946` |
| `src/main/webapp/WEB-INF/views/list-todos.jsp` | `319cc18dc2d831f6a218ec842dfd08a106f44fda7dda154d2ca504e7552663be` |
| `LICENSE` | `d4af2b5f03529f190721be7e65f8f34b511a31f2ab47f25b39192515629dc520` |

## Repository copies

### Verbatim upstream sample tree

- `src/test/resources/fixtures/spring-mvc/LICENSE`
- `src/test/resources/fixtures/spring-mvc/src/main/java/com/in28minutes/todo/TodoController.java`
- `src/test/resources/fixtures/spring-mvc/src/main/java/com/in28minutes/todo/TodoService.java`
- `src/test/resources/fixtures/spring-mvc/src/main/webapp/WEB-INF/views/todo.jsp`
- `src/test/resources/fixtures/spring-mvc/src/main/webapp/WEB-INF/views/list-todos.jsp`

Each file in this tree has the same SHA-256 as the matching upstream path listed above.

### Analyzer input fixtures

- `src/test/resources/fixtures/java/TodoController.java`
- `src/test/resources/fixtures/java/TodoService.java`
- `src/test/resources/fixtures/jsp/todo.jsp`
- `src/test/resources/fixtures/jsp/list-todos.jsp`

These are the same upstream bytes, copied to analyzer-oriented directories. They are not rewritten samples.

## Additional fixtures

Small self-authored fixtures added by later tasks must be labeled as authored fixtures in their task report; they must not be presented as upstream originals.
