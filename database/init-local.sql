-- =============================================================================
-- Script de inicialización para PostgreSQL local (sin Docker)
-- Instrucciones en pgAdmin 4:
--   1. Hacé clic derecho en "Databases" -> Create -> Database... y nombrala: cuidadores
--   2. Hacé clic derecho sobre la base "cuidadores" -> Query Tool
--   3. Pegá o abrí este archivo y ejecutalo (F5)
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cuidadores_app') THEN
        CREATE ROLE cuidadores_app WITH LOGIN PASSWORD 'cuidadores_app';
    ELSE
        ALTER ROLE cuidadores_app WITH LOGIN PASSWORD 'cuidadores_app';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS profiles (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT,
    nombre VARCHAR(255),
    apellido VARCHAR(255),
    zona VARCHAR(255),
    intereses VARCHAR(255),
    estudios VARCHAR(255),
    descripcion VARCHAR(1000)
);

GRANT CONNECT ON DATABASE cuidadores TO cuidadores_app;
GRANT USAGE, CREATE ON SCHEMA public TO cuidadores_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO cuidadores_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO cuidadores_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO cuidadores_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO cuidadores_app;

