-- Postgres 18 names each NOT NULL constraint after the table it is created on, and a table rename keeps that name.
-- The Cloud's history built expertise_tag_alias and expertise_tag_dismissal as *_v2 tables and renamed them, so a
-- database it created on 18 holds *_v2_<column>_not_null where every other database holds <table>_<column>_not_null.
-- One name everywhere, so a later migration may name these constraints. Elsewhere it renames nothing.
DO $$
DECLARE
    misnamed record;
BEGIN
    FOR misnamed IN
        SELECT c.relname AS table_name, n.conname AS constraint_name,
               c.relname || '_' || substring(n.conname FROM length(c.relname) + 5) AS target_name
        FROM pg_constraint n
        JOIN pg_class c ON c.oid = n.conrelid
        WHERE n.connamespace = 'public'::regnamespace
          AND n.contype = 'n'
          AND c.relname IN ('expertise_tag_alias', 'expertise_tag_dismissal')
          AND n.conname LIKE c.relname || '\_v2\_%'
    LOOP
        EXECUTE format('ALTER TABLE public.%I RENAME CONSTRAINT %I TO %I',
                       misnamed.table_name, misnamed.constraint_name, misnamed.target_name);
    END LOOP;
END
$$;
