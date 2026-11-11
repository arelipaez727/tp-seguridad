DO
$$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cuidadores_app') THEN
        CREATE ROLE cuidadores_app LOGIN PASSWORD 'cuidadores_app';
    END IF;
END
$$;

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

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE cuidadores TO cuidadores_app;
GRANT USAGE ON SCHEMA public TO cuidadores_app;
GRANT SELECT, INSERT, UPDATE ON users, profiles TO cuidadores_app;
GRANT USAGE, SELECT ON SEQUENCE users_id_seq, profiles_id_seq TO cuidadores_app;
