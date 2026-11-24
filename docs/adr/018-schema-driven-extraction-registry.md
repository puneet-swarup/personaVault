# ADR-018: Schema-Driven Extraction Registry

## Status
Proposed (2026-10-29)

## Context
`ExtractionService` today hard-codes a single insurance JSON schema in a Java string constant. Supporting a new document type (medical report, bank statement, ID) would mean editing Java, recompiling, and risking the existing pipeline.

ADR-017 defines a generic `entity` + `fact` model. To populate it for many domains without code changes per domain, extraction must be driven by declarative schemas.

## Decision
Introduce a schema registry: declarative definitions of what to extract from each document type.

### Schema location
`src/main/resources/schemas/<schema-id>.yaml` — versioned with the code, readable, reviewable in PRs.

### Schema shape (illustrative)
`yaml
id: insurance-policy
version: 1
document_type_hint: ["policy", "insurance", "premium"]
entity:
  type: POLICY
  canonical_name_from: "policyNumber"
facts:
  - key: policyNumber
    type: STRING
    required: false
  - key: expiryDate
    type: DATE
    required: true
  - key: annualPremium
    type: MONEY
    required: false
relationships:
  - key: insurer
    target_entity_type: INSURER
    from_fact: insurerName
confidence_rules:
  - when: "extracted_by=REGEX and validation=PASS"
    set: 0.95
  - when: "extracted_by=LLM"
    set: 0.75
validation:
  - rule: "expiryDate > effectiveDate"
    message: "expiry must follow effective date"
`

### Pipeline changes
1. Classifier (rules + small LLM) picks a schema id at ingest.
2. Prompt builder generates the LLM extraction prompt from the schema (fields, types, allowed enums).
3. Validator runs schema validation rules on the LLM output; failures mark the fact low-confidence or drop it.
4. Persister writes `entity` + `fact` rows using the schema's entity type and fact definitions.

### Registry API
`SchemaRegistry.get(schemaId)`, `SchemaRegistry.match(documentText)` — package-private for testing.

### Adding a domain
One new YAML file + a test fixture. No Java changes.

## Consequences
- Plus: New domains ship as data, not code
- Plus: Prompts and validation are co-located with the schema — easier to reason about
- Plus: Schemas are reviewable and diffable in PRs
- Plus: Unknown document types fall back to a generic `miscellaneous` schema rather than failing ingestion
- Minus: Schema authoring is a new skill; mitigated by templates and one reference schema per domain
- Minus: Prompt builder must be robust to schema typos; mitigated by schema validation at startup (fail fast)
- Minus: Very unusual layouts may still need custom code; mitigated by an escape hatch (`custom_extractor` field naming a class)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Hard-coded extraction per domain | Editing Java per domain; violates Open/Closed |
| Schemas stored in DB, editable in UI | Powerful but adds a schema-editing UI; defer until schemas stabilise |
| One mega-schema for all docs | LLM confusion; poor precision |
| Pure LLM with no schema | Unstable JSON shapes; no validation |

## Related
ADR-017 (fact model), ADR-021 (confidence from schema rules), ADR-027 (domain expansion)
