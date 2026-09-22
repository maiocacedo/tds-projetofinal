# Checkpoint 1 (Backend & DB Setup) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configurar a base do projeto Spring Boot, banco de dados MySQL com Flyway, segurança JWT e os endpoints iniciais de Usuário e Casa (Nosso Teto).

**Architecture:** API REST em Java estruturada no padrão MVC (Models, Repositories, Services, Controllers). Configuração inicial do Spring Security com filtros customizados para interceptar tokens JWT. Migrations gerenciadas rigorosamente via Flyway.

**Tech Stack:** Java 17+, Spring Boot (Web, Security, Data JPA), MySQL, Flyway, JWT (jjwt), JUnit 5, Mockito.

---

### Task 1: Scaffolding do Projeto Spring Boot e Configuração do Maven

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/nossoteto/NossoTetoApplication.java`
- Create: `src/main/resources/application.properties`

- [ ] **Step 1: Criar o arquivo `pom.xml` base**

Adicionar as dependências principais: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-test`, `mysql-connector-j`, `flyway-core`, e `flyway-mysql`.

- [ ] **Step 2: Criar a classe principal da aplicação**

```java
package com.nossoteto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NossoTetoApplication {
    public static void main(String[] args) {
        SpringApplication.run(NossoTetoApplication.class, args);
    }
}
```

- [ ] **Step 3: Configurar o `application.properties`**

```properties
spring.application.name=nosso-teto-api
spring.datasource.url=jdbc:mysql://localhost:3306/nossoteto_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=root
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/
git commit -m "chore: scaffold initial spring boot project configuration"
```

### Task 2: Configuração Inicial do Flyway e Tabela de Usuários

**Files:**
- Create: `src/main/resources/db/migration/V1__Create_users_and_houses.sql`
- Create: `src/main/java/com/nossoteto/model/User.java`
- Create: `src/main/java/com/nossoteto/repository/UserRepository.java`
- Create: `src/test/java/com/nossoteto/repository/UserRepositoryTest.java`

- [ ] **Step 1: Criar Migration V1**

```sql
CREATE TABLE houses (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    description TEXT,
    is_accepting_matches BOOLEAN DEFAULT TRUE
);

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    house_id VARCHAR(36) NULL,
    is_house_admin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_houses FOREIGN KEY (house_id) REFERENCES houses(id)
);
```

- [ ] **Step 2: Criar Teste de Repositório (UserRepositoryTest.java)**

*(Escrever um `@DataJpaTest` básico para verificar se a injeção do repositório funciona e salva um User).*

- [ ] **Step 3: Criar a Entidade User e Interface UserRepository**

```java
// Entidade JPA mapeando a tabela users. Campos: id (UUID), email, passwordHash.
```

- [ ] **Step 4: Executar testes para confirmar mapeamento**
Executar comando Maven para testes e confirmar aprovação.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: setup flyway V1 and user jpa repository"
```

### Task 3: Setup do Spring Security e JWT

**Files:**
- Create: `src/main/java/com/nossoteto/security/SecurityConfig.java`
- Create: `src/main/java/com/nossoteto/security/JwtTokenProvider.java`
- Create: `src/main/java/com/nossoteto/security/JwtAuthenticationFilter.java`

- [ ] **Step 1: Implementar o Provider e o Filter de JWT**
*(Adicionar lógicas de geração de token e filtro HTTP para validar `Authorization: Bearer`).*

- [ ] **Step 2: Configurar o SecurityConfig**
*(Bloquear todas as requisições exceto `/api/auth/**`, desativar CSRF e SessionCreationPolicy).*

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "feat: setup spring security and jwt configuration"
```

### Task 4: Endpoints de Autenticação (Auth Controller)

**Files:**
- Create: `src/main/java/com/nossoteto/controller/AuthController.java`
- Create: `src/main/java/com/nossoteto/service/AuthService.java`
- Create: `src/main/java/com/nossoteto/dto/LoginRequest.java`

- [ ] **Step 1: Escrever Teste Unitário para AuthService e AuthController**

- [ ] **Step 2: Implementar AuthService (Registro e Login com BCrypt)**

- [ ] **Step 3: Implementar AuthController (`/api/auth/register` e `/api/auth/login`)**

- [ ] **Step 4: Testar e Commit**

```bash
git commit -m "feat: implement auth login and registration endpoints"
```
