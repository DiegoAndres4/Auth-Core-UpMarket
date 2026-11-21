-- Archivo de cuentas eliminadas: al borrar una cuenta se conserva una
-- instantanea del registro y se libera el username para un registro nuevo.
-- No se guarda el hash de la contrasena: una cuenta cerrada no conserva
-- credenciales.

CREATE TABLE IF NOT EXISTS deleted_users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    original_user_id BIGINT       NOT NULL,
    username         VARCHAR(255) NOT NULL,
    roles            VARCHAR(255) NOT NULL,
    email_verified   BOOLEAN      NOT NULL,
    blocked          BOOLEAN      NOT NULL,
    created_at       DATETIME(6)  NULL,
    deleted_at       DATETIME(6)  NOT NULL,
    deleted_by       VARCHAR(255) NOT NULL,
    reason           VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_deleted_users_username (username),
    KEY idx_deleted_users_deleted_at (deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
