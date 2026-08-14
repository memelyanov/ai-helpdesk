-- Enables the pgvector extension in the target database. Idempotent — safe to re-run.
-- Runs automatically only when the container's data directory is empty; a changed copy of this
-- file has no effect on an already-initialised volume (see FR-024, quickstart.md "Reset").
CREATE EXTENSION IF NOT EXISTS vector;
