-- V7: wiki_page mirror table (ADR-022, Tier 3 synthesis).
-- The markdown files under ./data/wiki/ are canonical; this table is an index
-- over them for listing and search. If the two disagree, the file wins and the
-- row is rebuilt by the nightly recompile.
--
-- fact_refs and source_refs are intentionally NOT columns here. They live in the
-- page frontmatter, which is the canonical copy, and duplicating them in a
-- bigint[] column would create a second source of truth for the same data.
-- Add them as generated/derived columns only if a query needs them.

CREATE TABLE wiki_page (
    slug              VARCHAR(120) PRIMARY KEY,
    page_type         VARCHAR(20)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body_markdown     TEXT         NOT NULL,
    entity_id         BIGINT REFERENCES entity(id),
    needs_review      BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INTEGER      NOT NULL DEFAULT 1,
    last_compiled_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wiki_page_type CHECK (page_type IN ('ENTITY', 'TOPIC', 'TIMELINE', 'INDEX'))
);

CREATE INDEX idx_wiki_page_type   ON wiki_page (page_type);
CREATE INDEX idx_wiki_page_entity ON wiki_page (entity_id);
