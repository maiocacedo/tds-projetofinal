# Modelagem do Banco de Dados - Nosso Teto

Este documento descreve o esquema de banco de dados (MySQL) para o Checkpoint 1 da aplicação. O versionamento do banco será gerido via **Flyway**.

## Entidades Principais e Relacionamentos

### 1. `users` (Usuários)
- `id` (PK, UUID)
- `email` (VARCHAR, UNIQUE, NOT NULL)
- `password_hash` (VARCHAR, NOT NULL) - *Criptografado (Bcrypt)*
- `house_id` (FK para `houses`, NULLABLE)
- `is_house_admin` (BOOLEAN, DEFAULT FALSE)
- `created_at`, `updated_at` (TIMESTAMP)

### 2. `user_profiles` (Perfis de Match)
- `user_id` (PK, FK para `users`)
- `budget` (DECIMAL)
- `has_pets` (BOOLEAN)
- `is_smoker` (BOOLEAN)
- `profession` (VARCHAR)
- `bio` (TEXT)
- `allergies` (VARCHAR)
- `organization_level` (INT/ENUM)
- `visitor_tolerance` (INT/ENUM)
- `has_furniture` (BOOLEAN)

### 3. `houses` (Casas)
- `id` (PK, UUID)
- `name` (VARCHAR, NOT NULL)
- `address` (VARCHAR)
- `description` (TEXT)
- `is_accepting_matches` (BOOLEAN, DEFAULT TRUE)

### 4. `match_requests` (Solicitações de Match)
- `id` (PK, UUID)
- `user_id` (FK para `users`)
- `house_id` (FK para `houses`)
- `status` (ENUM: PENDING, ACCEPTED, REJECTED, CANCELLED)
- `created_at` (TIMESTAMP)

### 5. `match_votes` (Votos de Match)
- `id` (PK, UUID)
- `request_id` (FK para `match_requests`)
- `voter_id` (FK para `users`)
- `vote` (ENUM: APPROVE, REJECT)

### 6. `expenses` (Despesas)
- `id` (PK, UUID)
- `house_id` (FK para `houses`)
- `title` (VARCHAR)
- `total_amount` (DECIMAL)
- `due_date` (DATE)
- `attachment_url` (VARCHAR) - *Caminho seguro/criptografado*
- `split_type` (ENUM: EQUAL, CUSTOM_PENDING, CUSTOM_APPROVED)

### 7. `expense_splits` (Divisões da Despesa / Pagamentos)
- `id` (PK, UUID)
- `expense_id` (FK para `expenses`)
- `user_id` (FK para `users`)
- `amount_due` (DECIMAL)
- `is_paid` (BOOLEAN, DEFAULT FALSE)

### 8. `chores` (Tarefas)
- `id` (PK, UUID)
- `house_id` (FK para `houses`)
- `description` (VARCHAR)
- `chore_type` (ENUM: ROTATIONAL, FIXED)
- `current_assignee_id` (FK para `users`)
- `status` (ENUM: PENDING, COMPLETED, DELAYED)
- `last_reset_date` (DATE)

### 9. `groceries` (Itens de Mercado)
- `id` (PK, UUID)
- `house_id` (FK para `houses`)
- `item_name` (VARCHAR)
- `quantity` (INT)
- `assignee_id` (FK para `users`, NULLABLE)
- `is_purchased` (BOOLEAN, DEFAULT FALSE)
