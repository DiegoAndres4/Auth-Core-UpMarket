-- Esquema base de AuthCore. Generado a partir del esquema que creaba Hibernate
-- (MySQL 8, utf8mb4). Se usa CREATE TABLE IF NOT EXISTS para que la migración
-- también sirva como línea base en instalaciones que ya tienen las tablas.
-- Flyway está configurado con baseline-on-migrate=true y baseline-version=1:
-- una base existente sin historial se marca como V1 aplicada y no se toca.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    password_hash VARCHAR(255) NOT NULL,
    username      VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role    ENUM ('ADMIN', 'USER') DEFAULT NULL,
    UNIQUE KEY uk_user_roles_user_role (user_id, role),
    CONSTRAINT fk_user_roles_users
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
