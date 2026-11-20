-- Verificación de correo + bloqueo de usuarios.
-- Los usuarios que ya existían se marcan como verificados para que sigan
-- pudiendo iniciar sesión (la verificación solo aplica a registros nuevos).

ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blocked        BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
SET email_verified = TRUE;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evt_token_hash (token_hash),
    KEY idx_evt_user_id (user_id),
    CONSTRAINT fk_evt_users
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
