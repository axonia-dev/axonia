-- The unstemmed words of an artifact's search document, so a search can match the start of a word: English stemming
-- stores "incident" as "incid", which a typed "inciden" can never be a prefix of.
ALTER TABLE public.artifact
    ADD COLUMN search_vector_unstemmed tsvector
        GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, search_document)) STORED;

CREATE INDEX idx_artifact_search_vector_unstemmed ON public.artifact USING gin (search_vector_unstemmed);
