# Status report frontend

Vite + React + TypeScript. Talks to the Spring Boot backend through `/api`,
proxied to `http://localhost:8080` in dev (see `vite.config.ts`).

## Run it

```
npm install
npm run dev
```

Opens on `http://localhost:5173`. Make sure the backend is running first
(see `../backend/README.md`).

## Pages

- `/` — list and create projects.
- `/projects/:id` — upload the proposal and status report, see how many
  items were extracted from each, run the comparison, and download the
  Word report or PPT once results exist.

## Notes

- `src/api/client.ts` is the only place that knows about API paths - if you
  add an endpoint on the backend, add one function here rather than calling
  `fetch` from a component.
- Design tokens (color, type, spacing) live in `src/styles/tokens.css`;
  component styles are in `src/styles/global.css`. No CSS framework, just
  plain CSS with custom properties, so it's easy to retheme later.
