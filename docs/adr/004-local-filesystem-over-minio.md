# ADR-004: Local Filesystem Over MinIO

## Status
Accepted (2026-09-19)

## Context
Original documents (PDFs, images, spreadsheets) need to be stored so the user can view them, re-ingest after corrections, and export everything. Expected volume: **200-500 files, <5 GB total**. Single user, single machine.

## Decision
Store original files on the **local filesystem** at `./data/documents/`. No object storage service.

## Consequences
- ✅ Zero extra infrastructure (no container, no volume, no config)
- ✅ Simple Java `Path`/`Files` API for CRUD
- ✅ One-click export = `zip -r export.zip ./data/`
- ✅ Easy to back up (copy the directory)
- ❌ No S3-compatible API (not needed)
- ❌ No built-in versioning or deduplication (not needed for single-user)
- ❌ No access control (mitigated: Spring Security gates the web layer)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| MinIO | Extra container, volume, config, backup; zero benefit at 500 files |
| Store in PostgreSQL (bytea) | Bloats DB; complicates `pg_dump`; hard to export as zip; pollutes relational data |
| S3-compatible cloud storage | Violates "nothing leaves the machine" |
| Git LFS | Overkill; not designed for this use case |   