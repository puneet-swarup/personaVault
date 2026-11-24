# ADR-027: Domain Expansion Policy

## Status
Proposed (2026-10-29)

## Context
ADR-017/018 make the pipeline domain-agnostic: a new domain is a new extraction schema plus (optionally) new wiki page types. Without a policy, the project risks either expanding too broadly (shallow quality everywhere) or never expanding (stuck at insurance). This ADR defines what "supported domain" means, the order of expansion, and the exit criteria.

## Decision

### Definition of a supported domain
A domain is supported when:
1. One or more extraction schemas exist under `src/main/resources/schemas/`.
2. A schema test fixture with a redacted sample document passes extraction with expected fact values.
3. The wiki compiler produces at least one entity page per document type in that domain, with provenance footnotes.
4. `RenewalCheckerService`-style scheduled logic is either defined (deadlines apply) or explicitly waived.
5. Telemetry (ADR-025) shows the domain's facts and coverage.

### Framework-first principle (decided 2026-10-29, question 5)
The umbrella framework is built generically first, then specialised per domain — analogous to polymorphism in OOP: one interface (`entity` + `fact` + schema contract), many implementations (Policy, MedicalReport, BankStatement, ...). No domain-specific Java class is added until the generic path works end-to-end for that domain. Domains differ only by their schema YAML and their page-type templates; the pipeline, storage, provenance, confidence, contradiction, and wiki code stay shared.

Concretely, this means:
1. The generic `entity`/`fact` tables, `FactService`, and schema registry must exist and be tested before any second domain is added.
2. Insurance is re-expressed as the first schema on the generic path (not a special case in code).
3. Each subsequent domain is a specialisation: a new schema file plus fixtures — no new service classes.

### Expansion order
1. Insurance — already shipped (Policy schema, existing `policies` migrated to the generic model in Phase 2.5).
2. Financial — bank statements, credit card statements, tax documents, investment statements. Chosen first because it is structurally closest to insurance (issuers, periods, amounts) and validates the schema registry quickly.
3. Medical — lab reports, prescriptions, doctor notes, imaging reports. Chosen second because it exercises the model hardest (many entity types: provider, condition, medication, dosage, test) and is a stronger stress test.
4. Identity / Legal — IDs, passports, licenses, contracts, wills. Lower volume, important for completeness.
5. Property / Vehicle — registration, maintenance, warranties. Fits naturally with insurance cross-references.

### Out of scope (explicitly deferred)
- Email / web / sensor ingestion (per design discussion 2026-10-29).
- Multi-user domains (e.g. spouse/children sharing) until ADR-009 is revisited.
- Non-document sources (audio, images without OCR text).

### Adding a new page type
New wiki page types require an ADR amendment. v1 caps at: `entity`, `topic`, `timeline`, `index` (4 types). New entity subtypes (e.g. `MEDICATION`) do not require an ADR — they are schema-level.

### Schema ownership
Each schema file declares an owner (the author) and a `last_reviewed` date. Schemas not reviewed in 6 months are flagged in CI (not failed).

## Consequences
- Plus: Expansion is deliberate, testable, and sequenced by risk/reward
- Plus: Each domain ships with its own fixtures and telemetry
- Plus: No accidental scope creep from casual "just add a schema"
- Minus: Adding a domain still has ceremony; acceptable for a trust-first product
- Minus: Order may need re-evaluation if the user's real document mix differs; the policy allows reordering with a note

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Expand all domains at once | Shallow quality; hard to validate |
| Insurance only forever | Underserves the actual motto (personal vault) |
| Domain order by popularity | No data; picks wrong first domain |
| Ad-hoc expansion | Scope creep; no definition of "supported" |

## Related
ADR-015, ADR-017, ADR-018, ADR-025
