# Status report backend

Spring Boot 3.3 / Java 21. Compares an uploaded proposal document against an
uploaded status report and classifies each planned item as completed,
in progress, pending, at risk, or not started — using a fast rule-based
pass first, with Claude filling in only the cases the rules can't resolve.

## Run it locally

This was built and written in a sandbox with no access to Maven Central, so
it has **not** been compiled here — build it in your own IDE (Eclipse,
IntelliJ, VS Code) or from the command line once you've copied it over.

1. Start Postgres:
   ```
   docker compose up -d
   ```
2. Set your Anthropic key (used only for the LLM fallback passes):
   ```
   export ANTHROPIC_API_KEY=sk-ant-...
   ```
3. Run the app:
   ```
   ./mvnw spring-boot:run
   ```
   (or import as a Maven project in Eclipse/IntelliJ and run
   `StatusReportApplication`)

The API comes up on `http://localhost:8080`. Without `ANTHROPIC_API_KEY` set,
everything still works except the LLM fallback pass — items the rule-based
pass can't confidently parse or match will throw rather than silently fail,
so set the key before testing with real-world (non-templated) documents.

## API summary

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/projects` | Create a project |
| GET | `/api/projects` | List projects |
| POST | `/api/projects/{id}/proposal` | Upload the proposal (multipart `file`) |
| POST | `/api/projects/{id}/status-reports` | Upload a status report (multipart `file`) |
| GET | `/api/projects/{id}/items` | See what was extracted from each document |
| POST | `/api/projects/{id}/comparison/run` | Run the comparison, replaces previous results |
| GET | `/api/projects/{id}/comparison` | Get the last comparison results |
| GET | `/api/projects/{id}/report/word` | Download the Word report |
| GET | `/api/projects/{id}/report/ppt` | Download the PPT |

## Where the hybrid logic lives

- `service/extraction/RuleBasedExtractor` then `LlmExtractor`, tied together
  by `ExtractionOrchestrator`.
- `service/comparison/RuleBasedMatcher` then `LlmSemanticMatcher`, tied
  together by `ComparisonOrchestrator`.

Both orchestrators follow the same shape: run the cheap deterministic pass
first, only send what's left over to Claude, then merge.
