# ADR-022: Wiki as Markdown-on-Disk with Provenance (Obsidian-Compatible)

## Status
Accepted (2026-11-24) — increment 1 implemented: deterministic compiler, markdown files,
`wiki_page` mirror, provenance validator, incremental + nightly compile.
Deferred: LLM narrative pass, `WikiController` (in-app reading), contradiction-driven
`needsReview`, `.obsidian/` starter config (blocked by `data/` being gitignored).

## Context
ADR-015 requires a persistent synthesis layer. Options range from a custom in-app wiki UI to a graph database to plain markdown files.

The key insight from the pattern that inspired this architecture: the wiki is a codebase, the LLM is the programmer, and Obsidian is the IDE. Building a wiki UI from scratch would be a months-long detour that adds nothing to the core product.

Additionally, the NFR "100% local, nothing leaves the machine" (ADR-001) and "scheduled checks resume after reboot" favour files on disk over opaque stores.

## Decision

### Storage
The wiki lives at `./data/wiki/` as plain markdown files, one file per page, mirroring page types as subfolders:

`
data/wiki/
  index.md                      (auto-generated)
  entity/
    policy-health-hdfc-2026.md
    insurer-hdfc-ergo.md
    provider-dr-mehta.md
    condition-hypertension.md
  topic/
    insurance-overview.md
    medical-timeline.md
    financial-obligations.md
`

### File format
Each page is markdown with YAML frontmatter:

`markdown
---
slug: policy-health-hdfc-2026
type: entity
entity: POLICY:health-hdfc-2026
lastCompiledAt: 2026-10-29T08:00:00Z
needsReview: false
version: 3
sourceRefs: [doc:abc123, doc:def456]
factRefs: [fact:42, fact:43]
---
# HDFC Ergo Health Policy 2026

Coverage: INR 12,00,000 [^f:42]
Expiry: 2027-03-15 [^f:43]

## History
- 2024: INR 5,00,000 [^f:10]
- 2025: INR 10,00,000 [^f:22] (maternity added)
- 2026: INR 12,00,000 [^f:42]

## Links
- Insurer: [[insurer-hdfc-ergo]]
- Related topic: [[insurance-overview]]

[^f:42]: from policy_hdfc_2026.pdf, page 1
[^f:43]: from policy_hdfc_2026.pdf, page 2
`

### Wikilinks and graph
`[[slug]]` links are standard Obsidian syntax. Opening `./data/wiki/` as an Obsidian vault gives graph view, backlinks, and search for free. No custom graph UI in v1.

### Provenance
Every claim in a page carries `[^f:<factId>]` or `[^d:<docRef>]` and a matching footnote. The compiler emits these; the validator rejects a page with uncited sentences.

### Disk is canonical
The markdown file is the source of truth for narrative. The `wiki_page` DB table is an index/mirror for queryability (search, listing, fact refs), but if they diverge, the file wins and the row is rebuilt.

### `wiki_page` DB table (mirror)
| Column | Notes |
|---|---|
| `slug` | PK |
| `page_type` | entity / topic / timeline / index |
| `title` | |
| `body_markdown` | mirror of file contents |
| `entity_id` | FK nullable |
| `fact_refs` | BIGINT[] |
| `source_refs` | BIGINT[] |
| `needs_review` | bool |
| `version` | int |
| `last_compiled_at` | TIMESTAMPTZ |

### Compilation model
Per decision 2026-10-29: both incremental and nightly.
- On ingest: async, touch 1-3 affected pages, patch, write, update index.
- Nightly (safety net): full recompile from live facts; catches drift, orphan links, missing footnotes.

### `.obsidian/` config
The repo ships a minimal `.obsidian/` directory (graph view enabled, CSS snippets for provenance styling) so the folder opens as a usable vault out of the box. Documentation in README explains how to point Obsidian at `./data/wiki/`.

## Consequences
- Plus: Zero custom UI in v1; Obsidian provides graph, search, backlinks
- Plus: User can read, edit, and even hand-write pages without touching code
- Plus: Fully local, plaintext, backups trivial
- Plus: Wiki is git-friendly (diffable)
- Minus: Two stores (file + mirror) must stay in sync; mitigated by file-wins rule and nightly rebuild
- Minus: No in-app editing in v1; mitigated by Obsidian and a later WikiController (Phase 3)
- Minus: Markdown parsing fragility; mitigated by a strict parser and validator

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Custom wiki UI | Months of detour; reimplements Obsidian |
| Graph database (Neo4j/AGE) | Overkill for single-user; Postgres + markdown links suffice |
| DB-only markdown blobs | Not greppable or Obsidian-openable; opaque |
| HTML pages generated from DB | Not editable; poor diff |

## Implementation notes (increment 1, 2026-11-24)

What landed, and the two places the implementation departs from the sketch above.

**Pieces**
| Class | Role |
|---|---|
| `WikiSlugger` | deterministic slug: file name, mirror PK, wikilink target |
| `WikiMarkdownRenderer` | facts + links -> markdown with frontmatter and `[^f:<id>]` footnotes |
| `WikiPageValidator` | rejects a page with an uncited claim line (provenance gate) |
| `WikiFileStore` | atomic LF writes under `./data/wiki/<type>/<slug>.md` |
| `WikiIndexBuilder` | regenerates `index.md` from the mirror |
| `WikiCompilerService` | `compileEntity`, `compileForDocument`, nightly `compileAll` |
| `FactIngestionService` | extraction output -> `entity` + `fact` rows (fills Tier 2) |

**Rendering is deterministic, not LLM-authored (yet).** A page is a current-values table,
a `## History` section for superseded facts, `## Links` for entity references, and a
`## Sources` footnote block. Same facts in, same bytes out — which is what makes the
provenance rule testable. A narrative pass can layer on top later; it must keep the
footnotes or the validator rejects the page.

**Departures from the sketch**
1. `fact_refs` / `source_refs` are **not** columns on `wiki_page`. They live in the page
   frontmatter, which is canonical, so duplicating them in a `bigint[]` column would create
   a second source of truth. Add them as derived columns only if a query needs them.
2. `.obsidian/` is not shipped in the repo because the wiki root (`./data/wiki`) is
   gitignored along with the rest of `data/`. The folder still opens as an Obsidian vault;
   it just starts without the pre-baked graph/CSS settings.

**Trigger points.** Ingestion -> async extraction -> `FactIngestionService` -> 
`WikiCompilerService.compileForDocument(docId)` (incremental, 1-3 pages). A `@Scheduled`
`compileAll` runs at 02:30 daily as the drift safety net. Compile failures are logged and
never lose an extraction that already succeeded.

## Related
ADR-015, ADR-021 (provenance), ADR-023 (routing), ADR-026 (cascade)
