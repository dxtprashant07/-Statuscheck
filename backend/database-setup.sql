-- One-time PostgreSQL setup for the statusreport backend.
-- Run this in pgAdmin's Query Tool while connected as the "postgres" superuser
-- (right-click the server → Query Tool), or via psql:
--   psql -U postgres -f database-setup.sql
--
-- It creates a dedicated login role and an empty database it owns. Spring Boot
-- (ddl-auto: update) creates all the tables automatically on first start.

-- 1) Application login role (matches the defaults in application.yml).
CREATE ROLE statusreport WITH LOGIN PASSWORD 'statusreport';

-- 2) Application database, owned by that role.
CREATE DATABASE statusreport OWNER statusreport;

-- 3) Make sure the role can create objects in the public schema.
--    (PostgreSQL 15+ no longer grants this to everyone by default.)
--    Run these two AFTER connecting to the "statusreport" database:
--      in pgAdmin: open a new Query Tool on the "statusreport" database, then run:
GRANT ALL ON SCHEMA public TO statusreport;
ALTER SCHEMA public OWNER TO statusreport;
