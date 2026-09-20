# ADR-009: In-Memory User with Form Login

## Status
Accepted (2026-09-20)

## Context
The app requires authentication (NFR #5: "Web UI requires login"). We need a login mechanism that is:
- Simple (single user, no user management UI)
- Secure (password not stored in plaintext)
- Deployment-ready (same code works locally or on a cloud VPS)

## Decision
Use Spring Security form login with an in-memory `UserDetailsService`. The single user's credentials come from environment variables. Password is bcrypt-encoded at startup.

## Consequences
- ✅ Zero database tables for user management
- ✅ Deployment-ready: change env vars, no code change
- ✅ Bcrypt encoding: even a memory dump doesn't reveal the password
- ✅ Form login is familiar, accessible, no SPA auth complexity
- ❌ No user management (can't change password via UI) — acceptable for single-user
- ❌ Password is in env var (plaintext at rest in the shell/`.env` file) — mitigated by file permissions and the fact that it's a local app
- ❌ No MFA / session timeout beyond Spring defaults — acceptable for single-user local

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Database-backed `UserDetailsService` | Extra table, migration, no benefit for single user |
| Spring Security OAuth2 / SAML | Massive overkill for single-user local app |
| API key / token auth | No form login UX; user would need to copy-paste a token |
| No authentication | Violates NFR #5; PII app must be gated |
| `{noop}` plaintext password | Insecure even locally; not deployment-ready; fails security review |   