CREATE TABLE permissions (
    id INT PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(255) NULL
);

CREATE TABLE user_permissions (
    user_id VARCHAR(36) NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    scope VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

INSERT INTO permissions (id, name, description) VALUES
    (1, 'ROLE_MANAGE_PROFILE',   'Ver e editar o proprio perfil'),
    (2, 'ROLE_SEARCH_HOUSES',    'Buscar casas e ver detalhes/compatibilidade'),
    (3, 'ROLE_MANAGE_HOUSE',     'Criar casa, editar casa e remover moradores'),
    (4, 'ROLE_MATCH',            'Solicitar, listar e votar matches'),
    (5, 'ROLE_MANAGE_EXPENSES',  'Despesas, divisoes, votos e pagamentos'),
    (6, 'ROLE_MANAGE_CHORES',    'Tarefas domesticas'),
    (7, 'ROLE_MANAGE_GROCERIES', 'Lista de mercado'),
    (8, 'ROLE_VIEW_DASHBOARD',   'Mural da casa'),
    (9, 'ROLE_ADMIN_USERS',      'Administrar usuarios e permissoes');

INSERT INTO user_permissions (user_id, permission_id)
SELECT u.id, p.id FROM users u CROSS JOIN permissions p WHERE p.name <> 'ROLE_ADMIN_USERS';
