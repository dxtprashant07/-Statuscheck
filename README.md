# Statuscheck — AI project status report generator

Upload a project proposal, then upload a status report. The app extracts
the planned items from the proposal and the reported progress from the
status report, matches them up, and tells you what's completed, in
progress, pending, at risk, or not started — then generates a Word report
and a PPT from the result.

Matching is hybrid: a fast rule-based pass handles items worded the same
way in both documents, and only the leftovers (status report describes the
same task in different words) get sent to Claude for semantic matching.
Same pattern for extraction - structured bullet/numbered lines are parsed
with regex, free-form narrative paragraphs go to the LLM.

```
statusreport-app/
├── backend/    Spring Boot 3.3 / Java 21 - see backend/README.md
└── frontend/   Vite + React + TypeScript - see frontend/README.md
```

## Quick start

```
# Terminal 1
cd backend
docker compose up -d
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run

# Terminal 2
cd frontend
npm install
npm run dev
```

Then open `http://localhost:5173`, create a project, upload the proposal
and a status report, and run the comparison.

## A note on this sandbox

This was built in an environment without access to Maven Central, so the
Java side could not be compiled or run here — only written. The React
frontend *was* installed and built successfully here (`npm run build`
passes clean) since npm's registry was reachable. Build the backend in
your own IDE or from the command line once you've got it on your machine.
