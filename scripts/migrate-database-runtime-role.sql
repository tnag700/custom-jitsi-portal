\set ON_ERROR_STOP on
-- Run as the existing bootstrap superuser in ONE application database, with
-- consumers stopped. Set app_user to a NEW role; set app_db to current_database.
-- This intentionally fails if the role exists; never reassign all objects of
-- a bootstrap superuser (that can include system objects and other databases).
BEGIN;
SELECT current_database() = :'app_db' AND current_user <> :'app_user' AS valid_target \gset
\if :valid_target
\else
  \echo 'Wrong database or runtime identity; aborting migration.'
  ROLLBACK;
  DO $$ BEGIN RAISE EXCEPTION 'Wrong database or runtime identity'; END $$;
\endif
SELECT NOT EXISTS (
  SELECT 1 FROM pg_proc WHERE pronamespace = 'public'::regnamespace
  UNION ALL
  SELECT 1 FROM pg_type WHERE typnamespace = 'public'::regnamespace AND typrelid = 0 AND typelem = 0
  UNION ALL
  SELECT 1 FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind NOT IN ('r', 'p', 'S', 'v', 'm', 'i', 'I', 't')
  UNION ALL
  SELECT 1 FROM pg_extension WHERE extnamespace = 'public'::regnamespace
) AS supported_schema \gset
\if :supported_schema
\else
  \echo 'Custom public objects require an ownership review; aborting migration.'
  ROLLBACK;
  DO $$ BEGIN RAISE EXCEPTION 'Custom public objects require an ownership review'; END $$;
\endif
CREATE ROLE :"app_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"app_db" TO :"app_user";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"app_user";
-- Tables first: their owned sequences follow the table owner.
SELECT format('ALTER TABLE %I.%I OWNER TO %I', n.nspname, c.relname, :'app_user')
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
ORDER BY c.oid \gexec
SELECT format('ALTER %s %I.%I OWNER TO %I',
  CASE c.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' END,
  n.nspname, c.relname, :'app_user')
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('S', 'v', 'm') \gexec
COMMIT;
-- Set the password privately with psql \password <app_user> before starting a
-- consumer. Do not put a plaintext password in a -v argument or SQL history.
