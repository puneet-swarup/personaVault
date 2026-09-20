# ADR-008: AES-256-GCM Field-Level Encryption

## Status
Accepted (2026-09-19)

## Context
Even locally, the PostgreSQL data file and the `./data/documents/` directory are readable by any process running as the same OS user. A malware infection, accidental `cat` of the DB file, or a backup shared with a technician would expose PII (policy numbers, medical data, account numbers). Field-level encryption ensures sensitive data is **unreadable without the key**, even if the storage is compromised.

## Decision
Encrypt PII fields (policy numbers, medical identifiers, financial account numbers) at the **application layer** using **AES-256-GCM** before persisting to PostgreSQL. The encryption key is stored in a local file (`./data/.key`) with `600` permissions. In v2, optionally migrate to OS keychain (Keychain on macOS, DPAPI on Windows, libsecret on Linux).

Implementation: JPA `@Converter` for transparent encrypt-on-write / decrypt-on-read.

## Consequences
- ✅ PII is unreadable from raw DB dump, file access, or backup copy
- ✅ GCM mode provides **authenticated encryption** (tamper detection — modified ciphertext fails decryption)
- ✅ Transparent to application code via JPA `@Converter`
- ✅ Key is separate from data — compromising DB doesn't expose key
- ❌ Can't `SELECT` or `WHERE` on encrypted fields directly (mitigated: store a SHA-256 hash column for lookups)
- ❌ Key management adds a small complexity (single local file for v1)
- ❌ Slight CPU overhead (negligible: <1ms per field at this scale)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| PostgreSQL TDE (Transparent Data Encryption) | Encrypts entire file; can't selectively encrypt; requires Enterprise or `pgcrypto`; key still on same machine |
| pgcrypto column encryption | DB-level; key stored in DB (defeats purpose); less control over key lifecycle |
| Full-disk encryption only (BitLocker/FileVault/LUKS) | OS-level; doesn't protect against DB file being copied/shared/backup-exposed |
| No encryption | Acceptable for some, but medical + financial data warrants it; user explicitly requested |   