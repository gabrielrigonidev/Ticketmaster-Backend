CREATE TABLE IF NOT EXISTS tb_scopes (
    scope_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tb_roles (
    role_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tb_role_scopes (
    role_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, scope_id),
    CONSTRAINT fk_role_scopes_role FOREIGN KEY (role_id) REFERENCES tb_roles(role_id),
    CONSTRAINT fk_role_scopes_scope FOREIGN KEY (scope_id) REFERENCES tb_scopes(scope_id)
);

CREATE TABLE IF NOT EXISTS tb_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    role_id BIGINT NOT NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES tb_roles(role_id)
);
