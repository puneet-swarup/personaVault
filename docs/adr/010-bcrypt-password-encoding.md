# ADR-010: Bcrypt Password Encoding

## Status
Accepted (2026-09-20)

## Context
The single user's password must be stored securely in memory. Even for a local app:
- A memory dump (crash dump, debugger attach, `/proc/<pid>/mem`) could expose credentials
- The same code may be deployed to a cloud VPS where the threat model is stricter
- Using `{noop}` (plaintext) sets a bad precedent and fails any security review

## Decision
Use **BCrypt** (cost factor 10, default) via Spring Security's `BCryptPasswordEncoder`. The plaintext password from the env var is hashed at application startup; only the hash is retained in the `InMemoryUserDetailsManager`.

## Consequences
- ✅ Password is never stored or retained in plaintext in memory
- ✅ Bcrypt is salted (unique salt per hash) — identical passwords produce different hashes
- ✅ Computationally expensive (~100ms) — resists brute-force on a memory dump
- ✅ Deployment-ready: identical code on local or cloud
- ✅ Industry standard; passes any security audit
- ❌ ~100ms startup cost for the single hash (negligible)
- ❌ Can't reverse the hash to show the user their password (by design)
- ❌ Env var still holds plaintext (mitigated: file permissions, `.gitignore`)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| `{noop}` (plaintext) | Insecure; not deployment-ready; fails security intent |
| `{pbkdf2}` | Also good, but bcrypt is more widely recognized and audited |
| `{argon2}` | Stronger than bcrypt, but Spring Security support is less mature; overkill for single-user |
| OS keychain (DPAPI / Keychain / libsecret) | Platform-specific; adds native dependency; overkill for v1 |
| Hardcoded hash in source | Can't change password without redeploy; bad practice |   