# ADR-003: Thymeleaf + Vanilla JS + SSE Over React/HTMX

## Status
Accepted (2026-09-19)

## Context
The frontend needs four views: chat (with streaming), document upload/browse, dashboard, and notification panel. The user explicitly wants **no Node.js, no build step, no frontend framework**. Total expected JS: ~200 lines.

## Decision
Use **Thymeleaf** server-side templates + **vanilla JavaScript** + **Server-Sent Events** (via `SseEmitter` + `fetch()` with `ReadableStream`). No React, no Vue, no HTMX, no bundler, no Node.js.

## Consequences
- ✅ Zero build step — edit HTML/JS, refresh browser
- ✅ ~4-5 Thymeleaf templates + 1 JS file
- ✅ SSE streaming is straightforward: `SseEmitter` server-side, `fetch` + `getReader()` client-side
- ✅ No npm, no package.json, no `node_modules`
- ✅ Trivially debuggable (browser devtools, no source maps needed)
- ❌ No component reusability (acceptable: 4 pages, minimal overlap)
- ❌ No client-side routing (use full page loads; fine for this scope)
- ❌ No ecosystem (state management, testing utilities) — not needed

## Alternatives Considered
| Option | Why rejected |
|---|---|
| React + Vite | Node.js build step; overkill for 4 pages; user explicitly rejected |
| Vue + Vite | Same as React |
| HTMX | Good for form interactions, but SSE streaming is clunkier than raw `fetch` + `ReadableStream`; also adds a dependency |
| Angular / Svelte / Solid | All require build steps; massive overkill |
| Server-side rendered + full page reloads | Can't do streaming chat without SSE/WebSocket |   