CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS lessons (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    repo          text,
    paths         text[] NOT NULL DEFAULT '{}',
    component     text,
    language      text,
    tags          text[] NOT NULL DEFAULT '{}',
    kind          text NOT NULL CHECK (kind IN ('review_feedback','gate_failure','stuck_postmortem','adr','spec')),
    trigger       text NOT NULL,
    lesson        text NOT NULL,
    evidence      text[] NOT NULL DEFAULT '{}',
    embedding     vector(1024) NOT NULL,
    search_tsv    tsvector GENERATED ALWAYS AS (to_tsvector('english', trigger || ' ' || lesson)) STORED,
    hits          int NOT NULL DEFAULT 1,
    helped        int NOT NULL DEFAULT 0,
    ignored       int NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_used_at  timestamptz,
    status        text NOT NULL DEFAULT 'active' CHECK (status IN ('active','promoted','expired')),
    scope         text NOT NULL DEFAULT 'repo' CHECK (scope IN ('repo','shared'))
);

CREATE INDEX IF NOT EXISTS lessons_embedding_hnsw ON lessons USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS lessons_tsv ON lessons USING gin (search_tsv);
CREATE INDEX IF NOT EXISTS lessons_repo_status ON lessons (repo, status);
