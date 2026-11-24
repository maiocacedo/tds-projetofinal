# Checkpoint 3 (Segurança da API) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: este plano é executado pelo Coordenador com **gauntlet-orca** (Orca orchestration). Tarefas marcadas `Gauntlet: SIM` passam pelo Worker-Critic completo; as demais pelo fluxo simples (Builder → Nível 1 → revisão do Coordenador). Steps usam checkbox (`- [ ]`).

**Goal:** Proteger a API do Nosso Teto com OAuth 2 (Password Flow + Refresh Token rotativo em cookie) sobre JWT, permissões persistidas no banco, Basic Auth via profile, CORS, logout, criptografia de dados sensíveis e pipeline de CI — entrega em 2026-11-30.

**Architecture:** Spring Security Resource Server valida JWT HS256 emitidos por um endpoint próprio `/oauth/token` (grants `password` e `refresh_token`). Autorização em duas camadas: `@PreAuthorize` (permissão do banco + scope do client) e `HouseAccessValidator` (pertencimento à casa). Refresh token opaco, hash SHA-256 persistido, rotação atômica com detecção de reuso por família.

**Tech Stack:** Java 17, Spring Boot 3.2.0 (Web, Security, OAuth2 Resource Server, Data JPA), Nimbus JOSE (via starter), MySQL/H2, Flyway, JUnit 5, Mockito, MockMvc + spring-security-test, JaCoCo, Checkstyle, GitHub Actions, Orca + Antigravity (Gemini) + Claude Code.

**Spec:** `docs/superpowers/specs/2026-11-23-checkpoint-3-security-design.md` (decisões D1–D12, matriz §4, contrato OAuth §5, invariantes INV-01..INV-18).

## Global Constraints

- Spring Boot `3.2.0`, Java `17` (CI) — não atualizar versões.
- Padrão MVC (`model`, `repository`, `service`, `controller`, `dto`, `exception`, `security`); código e banco em inglês; Conventional Commits.
- Erros da API no formato `{"error":"..."}`; erros do `/oauth/token` no formato OAuth `{"error":"<code>","error_description":"..."}`.
- `src/test/resources/application.properties` sombreia o de `main`: toda propriedade nova deve existir nos dois.
- Proibido handler catch-all de `Exception` no `GlobalExceptionHandler` (INV-02).
- Nenhum valor padrão para `JWT_SECRET` e `APP_CRYPTO_KEY` em `src/main/resources/application.properties` (INV-14).
- Baseline de arquivos não rastreados a preservar intacto: `tds-projetofinal-checkpoint2-caio-macedo.zip`, `tds-projetofinal-checkpoint2-caio-macedo/`.
- Cobertura de linhas ≥ 80% (`mvn -B verify` falha abaixo disso a partir do T0).
- Comandos no Windows PowerShell 5.1: sem `&&`; use `;` ou `; if ($?) { ... }`.

---

## Orquestração (Coordenador = esta sessão Claude)

### Papéis e modelos

| Papel | Agente | Modelo |
|---|---|---|
| Coordenador + revisor final | sessão atual (Claude) | — |
| Builder (tarefas diretas) | `--agent antigravity` | `gemini-4-hydrogen-high` |
| Builder (tarefas complexas) | `--agent antigravity` | `gemini-4-neon-high` (confirmado no Passo 0 via `worker-read`: "Gemini 4 Neon (High)") |
| Crítico Nível 2 (só Gauntlet) | `--agent claude` (contexto isolado, somente leitura) | padrão do Claude Code |

### Mapa de tarefas

| Tarefa | Conteúdo | Builder | Gauntlet | PR |
|---|---|---|---|---|
| T0 | CI (GitHub Actions, JaCoCo gate 80%, Checkstyle aviso) | hydrogen | não | PR 1 |
| T1 | V3 (permissões, refresh_tokens), entidades, repos, permissões padrão no registro | hydrogen | não | PR 2 |
| T2 | Resource Server + `/oauth/token` (password), clients, scopes, JWT com authorities, remoção do JWT legado e do `/api/auth/login`, `getName()` nos controllers | neon | **SIM** | PR 2 |
| T3 | Refresh opaco + cookie + rotação atômica + reuso + `DELETE /oauth/token/revoke` | neon | **SIM** | PR 2 |
| T4 | Profile `basic-security` | hydrogen | não | PR 2 |
| T5 | Matriz `@PreAuthorize` + endpoints de admin + `AdminBootstrap` | neon | **SIM** | PR 2 |
| T6 | CORS com credenciais | hydrogen | não | PR 3 |
| T7 | AES-256-GCM em `allergies`/`bio` + V4 | neon | **SIM** | PR 3 |
| T8 | Postman, docs, zip de entrega | hydrogen | não | PR 3 |

> Ordem ajustada em relação à rodada de grilling: o profile Basic (antes "T2") vem **depois** do núcleo OAuth porque depende do `CommonSecurityConfig`/`JsonAuthHandlers` criados no T2 e da remoção do `SecurityConfig` legado (duas chains sem profile colidiriam).

### Passo 0 — Preparação (1x)

- [ ] `orca skills get orchestration` (carregar o guia vigente).
- [ ] `orca orchestration run-create` — Run "checkpoint-3-security".
- [ ] `git status --short` e registrar o baseline (só os dois itens do CP2).
- [ ] **Resolver o ID do modelo neon:** `orca orchestration worker-start --worktree current --agent antigravity --model gemini-4-neon-high --spec "Smoke test: responda apenas OK e encerre."` seguido de `orca orchestration worker-read --dispatch <id> --source auto`. Se o terminal mostrar o modelo neon, fixar o ID no plano; se falhar ou mostrar outro modelo, **parar e perguntar ao usuário** (oferecer `gemini-4-argon-high`, confirmado nos logs). Nunca substituir silenciosamente.

### Fluxo simples (Gauntlet: não)

1. Builder com Template 1 do gauntlet-orca (ownership = seção **Files** da tarefa; spec = os steps da tarefa copiados na íntegra).
2. `worker-read` para confirmar o modelo; encerrar o turno e aguardar `worker_done`.
3. Nível 1: `git status --short; git diff --check; mvn -B verify`.
4. Revisão do Coordenador sobre `git diff` + arquivos novos. Achado → worker **novo** com Template 3 → volta ao passo 3.
5. Commit seletivo (`git add <paths>`; nunca `-A`).

### Fluxo Gauntlet (Gauntlet: SIM)

1–3 iguais ao fluxo simples.
4. Crítico Nível 2: worker `--agent claude` com Template 2 contendo **apenas** a referência à spec (§ e INV aplicáveis listados na tarefa), instrução de ler `git diff` + lista explícita dos arquivos novos, e a saída real do Nível 1. Proibido incluir relato do Builder. Proibido o Crítico rodar a suíte inteira.
5. `REPROVADO` → Template 3 → worker novo → Nível 1 → Crítico (rodada N+1, com decisões do Coordenador sobre achados rejeitados). Anti-estagnação: mesma categoria de falha 2x seguidas → worker novo limpo **ou** promoção hydrogen→neon.
6. `APROVADO` (0 achados) → commit seletivo.

### Fluxo de PRs

- **PR 1** — branch `docs/checkpoint-3-plan-and-ci` (já criada): commit da spec + plano (Coordenador), T0. `git push -u origin docs/checkpoint-3-plan-and-ci`; `gh pr create --base main`. Aguardar o check verde. **Merge só com autorização do usuário.** Depois do merge:
  - Ler o nome real do check: `gh api repos/maiocacedo/tds-projetofinal/commits/<sha-do-merge>/check-runs -q ".check_runs[].name"` (esperado: `build`).
  - Branch protection (repo público): `gh api -X PUT repos/maiocacedo/tds-projetofinal/branches/main/protection --input protection.json` com `{"required_status_checks":{"strict":true,"contexts":["<nome-lido>"]},"enforce_admins":false,"required_pull_request_reviews":null,"restrictions":null}` (arquivo JSON no scratchpad, gravado em UTF-8).
- **PR 2** — `git switch main; git pull; git switch -c feat/checkpoint-3-auth-core`: T1→T2→T3→T4→T5. Antes de abrir: revisão completa do Coordenador (skill `requesting-code-review`) sobre `git diff main...HEAD`. Merge com autorização.
- **PR 3** — `feat/checkpoint-3-hardening` a partir da `main` atualizada: T6→T7→T8. Mesmo rito. Após o merge: gerar o zip de entrega (T8 Step 6).

### Cronograma (entrega 30/11)

| Data | Marco |
|---|---|
| 23–24/11 | Passo 0, PR 1 (spec, plano, CI) + branch protection |
| 24–27/11 | PR 2 (T1–T5) |
| 27–29/11 | PR 3 (T6–T8) |
| 29/11 | Zip, ensaio da demo no Postman |
| 30/11 | Entrega |

---

### Task 0: Pipeline de CI (GitHub Actions + JaCoCo + Checkstyle)

**Gauntlet:** não · **Builder:** hydrogen · **PR:** 1

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `pom.xml` (seção `<build><plugins>`)

**Interfaces:**
- Produces: check de CI chamado `build` em todo PR; `mvn -B verify` falha com cobertura de linhas < 80%.

- [ ] **Step 1: Adicionar plugins ao `pom.xml`** (dentro de `<build><plugins>`, após o `spring-boot-maven-plugin`)

```xml
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.12</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                    <execution>
                        <id>check</id>
                        <phase>verify</phase>
                        <goals><goal>check</goal></goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.80</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.1</version>
                <configuration>
                    <configLocation>google_checks.xml</configLocation>
                    <consoleOutput>false</consoleOutput>
                    <failOnViolation>false</failOnViolation>
                    <failsOnError>false</failsOnError>
                    <includeTestSourceDirectory>false</includeTestSourceDirectory>
                </configuration>
                <executions>
                    <execution>
                        <id>checkstyle-report</id>
                        <phase>verify</phase>
                        <goals><goal>check</goal></goals>
                    </execution>
                </executions>
            </plugin>
```

Não adicionar `<argLine>` ao Surefire (o `prepare-agent` injeta o agente via propriedade `argLine`).

- [ ] **Step 2: Rodar localmente e conferir o gate**

Run: `mvn -B verify`
Expected: `BUILD SUCCESS`; `target/site/jacoco/index.html` gerado; log contém `All coverage checks have been met.`

- [ ] **Step 3: Provar que o gate falha (teste negativo, sem commitar)**

Run: `mvn -B verify "-Djacoco.haltOnFailure=true" "-Dtest=VotingEvaluatorTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: `BUILD FAILURE` com `Rule violated for bundle nosso-teto-api: lines covered ratio is 0.xx, but expected minimum is 0.80`.

- [ ] **Step 4: Criar `.github/workflows/ci.yml`**

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build, test, coverage gate and lint
        run: mvn -B verify

      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco

      - name: Upload Checkstyle report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: checkstyle-report
          path: target/checkstyle-result.xml
          if-no-files-found: ignore
```

- [ ] **Step 5: Commit**

```bash
git add pom.xml .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions pipeline with JaCoCo 80% line gate and Checkstyle report"
```

Após o push do PR 1: confirmar que o job `build` roda verde no GitHub (`gh pr checks`). Se falhar só no Linux (ex.: diferença de maiúsculas em caminho), tratar como achado do Nível 1.

---

### Task 1: Fundação — Migration V3, Permissões, Refresh Tokens, Permissões Padrão no Registro

**Gauntlet:** não · **Builder:** hydrogen · **PR:** 2

**Files:**
- Create: `src/main/resources/db/migration/V3__Security_permissions_and_refresh_tokens.sql`
- Create: `src/main/java/com/nossoteto/model/Permission.java`
- Create: `src/main/java/com/nossoteto/model/RefreshToken.java`
- Create: `src/main/java/com/nossoteto/repository/PermissionRepository.java`
- Create: `src/main/java/com/nossoteto/repository/RefreshTokenRepository.java`
- Create: `src/main/java/com/nossoteto/security/Permissions.java`
- Modify: `src/main/java/com/nossoteto/model/User.java`
- Modify: `src/main/java/com/nossoteto/repository/UserRepository.java`
- Modify: `src/main/java/com/nossoteto/service/AuthService.java` (só `registerUser` e o construtor)
- Modify: `src/test/java/com/nossoteto/service/AuthServiceTest.java`
- Modify: `src/test/java/com/nossoteto/integration/MySqlSchemaCompatibilityTest.java`
- Create: `src/test/java/com/nossoteto/integration/PermissionSeedIntegrationTest.java`

**Interfaces:**
- Produces:
  - `Permissions` constantes `String`: `MANAGE_PROFILE="ROLE_MANAGE_PROFILE"`, `SEARCH_HOUSES`, `MANAGE_HOUSE`, `MATCH`, `MANAGE_EXPENSES`, `MANAGE_CHORES`, `MANAGE_GROCERIES`, `VIEW_DASHBOARD`, `ADMIN_USERS`; `Set<String> DEFAULT_USER` (todas menos `ADMIN_USERS`).
  - `UserRepository.findPermissionNamesByUserId(String userId): List<String>`
  - `PermissionRepository.findByNameIn(Collection<String>): List<Permission>`; `findByNameNot(String): List<Permission>`
  - `RefreshTokenRepository.findByTokenHash(String): Optional<RefreshToken>`; `markUsed(String id, LocalDateTime now): int`; `revokeFamily(String familyId, LocalDateTime now): int`
  - `User.getPermissions(): Set<Permission>` (LAZY)
  - `AuthService(UserRepository, PermissionRepository, PasswordEncoder, JwtTokenProvider)` — o `JwtTokenProvider` sai no T2.

- [ ] **Step 1: Escrever os testes que falham**

`src/test/java/com/nossoteto/integration/PermissionSeedIntegrationTest.java`:

```java
package com.nossoteto.integration;

import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionSeedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void migrationSeedsTheNinePermissionCatalog() {
        assertEquals(9, permissionRepository.count());
    }

    @Test
    void registeredUserReceivesDefaultPermissionsWithoutAdmin() throws Exception {
        String email = "perm-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Perm\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated());

        String userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertEquals(Permissions.DEFAULT_USER, new HashSet<>(userRepository.findPermissionNamesByUserId(userId)));
    }
}
```

Em `AuthServiceTest`: adicionar `@Mock private PermissionRepository permissionRepository;`, construir `new AuthService(userRepository, permissionRepository, passwordEncoder, jwtTokenProvider)` e adicionar:

```java
    @Test
    void registerShouldGrantDefaultPermissions() {
        Permission manageProfile = new Permission(1, Permissions.MANAGE_PROFILE, "x");
        when(userRepository.findByEmail("novo@nossoteto.com")).thenReturn(Optional.empty());
        when(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)).thenReturn(List.of(manageProfile));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerUser(new RegisterRequest("Novo", "novo@nossoteto.com", "senha123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Set.of(manageProfile), saved.getValue().getPermissions());
    }
```

(O `passwordEncoder` do teste é um `BCryptPasswordEncoder` real — não fazer stub. Imports novos: `Permission`, `Permissions`, `PermissionRepository`, `Set`, `List`. Nos testes de registro já existentes, adicionar o stub `when(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)).thenReturn(List.of());` se o Mockito strict reclamar.)

Em `MySqlSchemaCompatibilityTest`: adicionar `.addAnnotatedClass(Permission.class)` e `.addAnnotatedClass(RefreshToken.class)` à lista.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -B test "-Dtest=PermissionSeedIntegrationTest,AuthServiceTest,MySqlSchemaCompatibilityTest"`
Expected: FAIL de compilação (`Permission`, `PermissionRepository`, `Permissions` inexistentes).

- [ ] **Step 3: Migration `V3__Security_permissions_and_refresh_tokens.sql`**

```sql
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
```

- [ ] **Step 4: Código de produção**

`src/main/java/com/nossoteto/security/Permissions.java`:

```java
package com.nossoteto.security;

import java.util.Set;

public final class Permissions {
    public static final String MANAGE_PROFILE = "ROLE_MANAGE_PROFILE";
    public static final String SEARCH_HOUSES = "ROLE_SEARCH_HOUSES";
    public static final String MANAGE_HOUSE = "ROLE_MANAGE_HOUSE";
    public static final String MATCH = "ROLE_MATCH";
    public static final String MANAGE_EXPENSES = "ROLE_MANAGE_EXPENSES";
    public static final String MANAGE_CHORES = "ROLE_MANAGE_CHORES";
    public static final String MANAGE_GROCERIES = "ROLE_MANAGE_GROCERIES";
    public static final String VIEW_DASHBOARD = "ROLE_VIEW_DASHBOARD";
    public static final String ADMIN_USERS = "ROLE_ADMIN_USERS";

    public static final Set<String> DEFAULT_USER = Set.of(
            MANAGE_PROFILE, SEARCH_HOUSES, MANAGE_HOUSE, MATCH,
            MANAGE_EXPENSES, MANAGE_CHORES, MANAGE_GROCERIES, VIEW_DASHBOARD);

    private Permissions() {}
}
```

`src/main/java/com/nossoteto/model/Permission.java`:

```java
package com.nossoteto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "description")
    private String description;

    public Permission() {}

    public Permission(Integer id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission other)) return false;
        return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() { return Objects.hashCode(name); }
}
```

`src/main/java/com/nossoteto/model/RefreshToken.java`:

```java
package com.nossoteto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "scope", nullable = false, length = 100)
    private String scope;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

`src/main/java/com/nossoteto/repository/PermissionRepository.java`:

```java
package com.nossoteto.repository;

import com.nossoteto.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    List<Permission> findByNameIn(Collection<String> names);
    List<Permission> findByNameNot(String name);
}
```

`src/main/java/com/nossoteto/repository/RefreshTokenRepository.java`:

```java
package com.nossoteto.repository;

import com.nossoteto.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.usedAt = :now where r.id = :id and r.usedAt is null")
    int markUsed(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revokedAt = :now where r.familyId = :familyId and r.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}
```

`User.java` — adicionar (com imports `jakarta.persistence.*`, `java.util.HashSet`, `java.util.Set`):

```java
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
```

`UserRepository.java` — adicionar (imports `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`):

```java
    @Query("select p.name from User u join u.permissions p where u.id = :userId")
    List<String> findPermissionNamesByUserId(@Param("userId") String userId);
```

`AuthService.java` — injetar `PermissionRepository` (construtor `AuthService(UserRepository, PermissionRepository, PasswordEncoder, JwtTokenProvider)`) e, em `registerUser`, antes do `save`:

```java
        user.setPermissions(new HashSet<>(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)));
```

Anotar `registerUser` com `@Transactional` (`org.springframework.transaction.annotation.Transactional`).

- [ ] **Step 5: Rodar os testes focados**

Run: `mvn -B test "-Dtest=PermissionSeedIntegrationTest,AuthServiceTest,MySqlSchemaCompatibilityTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V3__Security_permissions_and_refresh_tokens.sql src/main/java/com/nossoteto/model/Permission.java src/main/java/com/nossoteto/model/RefreshToken.java src/main/java/com/nossoteto/repository/PermissionRepository.java src/main/java/com/nossoteto/repository/RefreshTokenRepository.java src/main/java/com/nossoteto/security/Permissions.java src/main/java/com/nossoteto/model/User.java src/main/java/com/nossoteto/repository/UserRepository.java src/main/java/com/nossoteto/service/AuthService.java src/test/java/com/nossoteto/service/AuthServiceTest.java src/test/java/com/nossoteto/integration/MySqlSchemaCompatibilityTest.java src/test/java/com/nossoteto/integration/PermissionSeedIntegrationTest.java
git commit -m "feat: add V3 migration with permissions catalog, refresh tokens and default user permissions"
```

---

### Task 2: OAuth 2 Resource Server + `/oauth/token` (Password Grant)

**Gauntlet:** SIM · **Builder:** neon · **PR:** 2
**Crítico foca em:** spec §2, §5.1, §5.4; INV-01, INV-02, INV-03, INV-04, INV-05, INV-11, INV-13, INV-14, INV-18.

**Files:**
- Modify: `pom.xml` (remover `jjwt-api`, `jjwt-impl`, `jjwt-jackson`; adicionar `spring-boot-starter-oauth2-resource-server` e `spring-security-test` com `<scope>test</scope>`)
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application.properties`
- Delete: `src/main/java/com/nossoteto/security/SecurityConfig.java`, `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`
- Delete: `src/test/java/com/nossoteto/security/JwtTokenProviderTest.java`
- Delete: `src/main/java/com/nossoteto/dto/AuthResponse.java`, `src/main/java/com/nossoteto/dto/LoginRequest.java`
- Create: `src/main/java/com/nossoteto/security/SecurityProperties.java`
- Create: `src/main/java/com/nossoteto/security/CommonSecurityConfig.java`
- Create: `src/main/java/com/nossoteto/security/OAuthSecurityConfig.java`
- Create: `src/main/java/com/nossoteto/security/JsonAuthHandlers.java`
- Create: `src/main/java/com/nossoteto/security/OAuthException.java`
- Create: `src/main/java/com/nossoteto/security/ClientAuthenticator.java`
- Create: `src/main/java/com/nossoteto/security/AccessTokenService.java`
- Create: `src/main/java/com/nossoteto/security/OAuthTokenService.java`
- Create: `src/main/java/com/nossoteto/security/TokenController.java`
- Modify: `src/main/java/com/nossoteto/controller/AuthController.java` (remover `login`)
- Modify: `src/main/java/com/nossoteto/service/AuthService.java` (remover `loginUser` e o `JwtTokenProvider`)
- Modify: os 7 controllers com `extractUserId`: `ChoreController`, `DashboardController`, `ExpenseController`, `GroceryController`, `HouseController`, `MatchController`, `UserController`
- Modify: `src/test/java/com/nossoteto/service/AuthServiceTest.java` (remover os 3 testes de login, o campo `JwtTokenProvider` e os imports de `AuthResponse`/`LoginRequest`/`JwtTokenProvider`)
- Modify: `src/test/java/com/nossoteto/controller/ExpenseControllerTest.java`, `HouseControllerTest.java`, `MatchControllerTest.java`, `UserControllerTest.java` (remover os `@MockBean JwtAuthenticationFilter` / `@MockBean JwtTokenProvider` e seus imports — sem isso a árvore de testes não compila)
- Modify: `src/test/java/com/nossoteto/integration/NossoTetoIntegrationTest.java` (`loginUser`)
- Modify: `src/test/java/com/nossoteto/integration/ApiErrorHandlingTest.java` (login no `@BeforeEach` e teste de senha errada)
- Create: `src/test/java/com/nossoteto/security/ClientAuthenticatorTest.java`
- Create: `src/test/java/com/nossoteto/integration/OAuthPasswordFlowIntegrationTest.java`

**Interfaces:**
- Consumes (T1): `UserRepository.findPermissionNamesByUserId`, `Permissions`.
- Produces:
  - `SecurityProperties` record: `accessTokenTtl(): Duration`, `refreshTokenTtl(): Duration`, `cookie(): Cookie{secure(): boolean}`, `clients(): List<Client{clientId(), clientSecret(), scopes(): Set<String>}>`.
  - `ClientAuthenticator.authenticate(String authorizationHeader): SecurityProperties.Client`
  - `AccessTokenService.issue(String userId, String clientId, Set<String> scopes): AccessTokenService.IssuedAccessToken(String value, long expiresInSeconds, String scope)`
  - `OAuthTokenService.passwordGrant(Client, String username, String password, String requestedScope): OAuthTokenService.TokenResult(IssuedAccessToken accessToken, String userId, String refreshToken)` — `refreshToken` é `null` até o T3.
  - `OAuthException` com fábricas `invalidClient()`, `invalidGrant(String)`, `invalidScope()`, `unsupportedGrantType()`, `invalidRequest(String)`; getters `getError()`, `getStatus()`.
  - `JsonAuthHandlers` (`AuthenticationEntryPoint` + `AccessDeniedHandler`), mensagens `Authentication required` / `Insufficient permission`.
  - Beans: `Clock`, `PasswordEncoder`, `JwtDecoder`, `JwtEncoder`, `JwtAuthenticationConverter`.

- [ ] **Step 1: Propriedades**

`src/main/resources/application.properties` — substituir as linhas `app.jwt.*` por:

```properties
app.jwt.secret=${JWT_SECRET}
app.security.access-token-ttl=${JWT_ACCESS_TTL:PT15M}
app.security.refresh-token-ttl=${JWT_REFRESH_TTL:P7D}
app.security.cookie.secure=${COOKIE_SECURE:false}
app.security.clients[0].client-id=nosso-teto-web
app.security.clients[0].client-secret=${WEB_CLIENT_SECRET:web-dev-secret}
app.security.clients[0].scopes=read,write
app.security.clients[1].client-id=nosso-teto-mobile
app.security.clients[1].client-secret=${MOBILE_CLIENT_SECRET:mobile-dev-secret}
app.security.clients[1].scopes=read
```

`src/test/resources/application.properties` — substituir `app.jwt.expiration-ms=...` por:

```properties
app.security.access-token-ttl=PT15M
app.security.refresh-token-ttl=P7D
app.security.cookie.secure=false
app.security.clients[0].client-id=nosso-teto-web
app.security.clients[0].client-secret=web-test-secret
app.security.clients[0].scopes=read,write
app.security.clients[1].client-id=nosso-teto-mobile
app.security.clients[1].client-secret=mobile-test-secret
app.security.clients[1].scopes=read
```

(manter `app.jwt.secret=NossoTetoTestSecretKeyForJwtGenerationNeedsToBeVeryLong256BitsAtLeast`.)

- [ ] **Step 2: Testes que falham**

`src/test/java/com/nossoteto/security/ClientAuthenticatorTest.java`:

```java
package com.nossoteto.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientAuthenticatorTest {

    private final ClientAuthenticator authenticator = new ClientAuthenticator(new SecurityProperties(
            Duration.ofMinutes(15), Duration.ofDays(7), new SecurityProperties.Cookie(false),
            List.of(new SecurityProperties.Client("web", "s3cret", Set.of("read", "write")))));

    private static String basic(String raw) {
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validCredentialsReturnTheClient() {
        assertEquals("web", authenticator.authenticate(basic("web:s3cret")).clientId());
    }

    @Test
    void wrongSecretIsInvalidClient() {
        OAuthException ex = assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("web:nope")));
        assertEquals("invalid_client", ex.getError());
    }

    @Test
    void unknownClientMissingHeaderMalformedBase64AndNoColonAreInvalidClient() {
        assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("ghost:s3cret")));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(null));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("Basic %%%"));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("websecret")));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("Bearer abc"));
    }
}
```

`src/test/java/com/nossoteto/integration/OAuthPasswordFlowIntegrationTest.java`:

```java
package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthPasswordFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder jwtDecoder;

    private String email;
    private String userId;

    @BeforeEach
    void register() throws Exception {
        email = "oauth-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult r = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"OAuth\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        userId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions token(String client, String secret, String... params) throws Exception {
        var req = post("/oauth/token").with(httpBasic(client, secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mockMvc.perform(req);
    }

    @Test
    void passwordGrantReturnsBearerJwtWithScopesAndPermissions() throws Exception {
        MvcResult r = token("nosso-teto-web", "web-test-secret",
                "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.scope").value("read write"))
                .andExpect(jsonPath("$.user_id").value(userId))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());
        assertEquals(userId, jwt.getSubject());
        assertEquals("read write", jwt.getClaimAsString("scope"));
        assertEquals("nosso-teto-web", jwt.getClaimAsString("client_id"));
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        assertTrue(authorities.contains("ROLE_MANAGE_PROFILE"));
        assertFalse(authorities.contains("ROLE_ADMIN_USERS"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + body.get("access_token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void wrongClientSecretIs401InvalidClientWithChallenge() throws Exception {
        token("nosso-teto-web", "wrong", "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"nosso-teto\""))
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void missingClientAuthenticationIs401() throws Exception {
        mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void wrongPasswordAndUnknownUserAre400InvalidGrant() throws Exception {
        token("nosso-teto-web", "web-test-secret", "grant_type", "password", "username", email, "password", "errada")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        token("nosso-teto-web", "web-test-secret", "grant_type", "password", "username", "ghost@x.com", "password", "senha123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void unsupportedAndMissingGrantType() throws Exception {
        token("nosso-teto-web", "web-test-secret", "grant_type", "client_credentials")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        token("nosso-teto-web", "web-test-secret")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void mobileClientCannotRequestWriteScopeAndDefaultsToRead() throws Exception {
        token("nosso-teto-mobile", "mobile-test-secret",
                "grant_type", "password", "username", email, "password", "senha123", "scope", "read write")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
        token("nosso-teto-mobile", "mobile-test-secret",
                "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("read"));
    }

    @Test
    void tamperedOrMissingBearerIs401AndLegacyLoginIsGone() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
```

`NossoTetoIntegrationTest.loginUser` — substituir o corpo por:

```java
    private String loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth/token")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
    }
```

`ApiErrorHandlingTest` — no `@BeforeEach`, trocar o login por uma chamada `POST /oauth/token` com header `Authorization: Basic ` + base64(`nosso-teto-web:web-test-secret`), `Content-Type: application/x-www-form-urlencoded` e corpo `grant_type=password&username=<URLEncoder.encode(email, UTF_8)>&password=senha123`, lendo `access_token`. Adicionar ao helper `send` um overload que aceite headers extras (ou criar `sendForm(path, body)` no próprio arquivo). Trocar `loginWithWrongPasswordShouldReturn401` por `tokenWithWrongPasswordShouldReturn400InvalidGrant` (status 400, `$.error == "invalid_grant"`).

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -B test "-Dtest=ClientAuthenticatorTest,OAuthPasswordFlowIntegrationTest"`
Expected: FAIL de compilação (classes inexistentes).

- [ ] **Step 4: Implementação**

`SecurityProperties.java`:

```java
package com.nossoteto.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl,
        @DefaultValue Cookie cookie,
        List<Client> clients) {

    public record Cookie(@DefaultValue("false") boolean secure) {}

    public record Client(String clientId, String clientSecret, Set<String> scopes) {}
}
```

`OAuthException.java`:

```java
package com.nossoteto.security;

import org.springframework.http.HttpStatus;

public class OAuthException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuthException invalidClient() {
        return new OAuthException("invalid_client", "Client authentication failed", HttpStatus.UNAUTHORIZED);
    }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidScope() {
        return new OAuthException("invalid_scope", "Requested scope is not allowed for this client", HttpStatus.BAD_REQUEST);
    }

    public static OAuthException unsupportedGrantType() {
        return new OAuthException("unsupported_grant_type", "Grant type not supported", HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, HttpStatus.BAD_REQUEST);
    }

    public String getError() { return error; }
    public HttpStatus getStatus() { return status; }
}
```

`JsonAuthHandlers.java`:

```java
package com.nossoteto.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Insufficient permission");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
```

`CommonSecurityConfig.java`:

```java
package com.nossoteto.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class CommonSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

`OAuthSecurityConfig.java`:

```java
package com.nossoteto.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@Profile("!basic-security")
public class OAuthSecurityConfig {

    private final SecretKey secretKey;

    public OAuthSecurityConfig(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes");
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    @Order(1)
    public SecurityFilterChain tokenEndpointChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/oauth/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Client authentication is done by ClientAuthenticator against the client registry, never the user store.
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http, JsonAuthHandlers handlers) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        // "/error" must stay open: Spring forwards failed requests there without the JWT.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter(); // scope -> SCOPE_*
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> result = new ArrayList<>(scopes.convert(jwt));
            List<String> permissions = jwt.getClaimAsStringList("authorities");
            if (permissions != null) {
                permissions.forEach(p -> result.add(new SimpleGrantedAuthority(p)));
            }
            return result;
        });
        return converter; // principal name = "sub" (user id)
    }
}
```

`ClientAuthenticator.java`:

```java
package com.nossoteto.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("!basic-security")
public class ClientAuthenticator {

    private final Map<String, SecurityProperties.Client> clients;

    public ClientAuthenticator(SecurityProperties properties) {
        this.clients = properties.clients().stream()
                .collect(Collectors.toUnmodifiableMap(SecurityProperties.Client::clientId, Function.identity()));
    }

    public SecurityProperties.Client authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw OAuthException.invalidClient();
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw OAuthException.invalidClient();
        }
        int separator = decoded.indexOf(':');
        if (separator <= 0) {
            throw OAuthException.invalidClient();
        }
        SecurityProperties.Client client = clients.get(decoded.substring(0, separator));
        String presented = decoded.substring(separator + 1);
        String expected = client != null ? client.clientSecret() : "";
        boolean secretMatches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
        if (client == null || !secretMatches) {
            throw OAuthException.invalidClient();
        }
        return client;
    }
}
```

`AccessTokenService.java`:

```java
package com.nossoteto.security;

import com.nossoteto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@Profile("!basic-security")
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final Duration ttl;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, UserRepository userRepository,
                              SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    public IssuedAccessToken issue(String userId, String clientId, Set<String> scopes) {
        // Permissions are re-read from the database on every issuance (INV-05).
        List<String> authorities = userRepository.findPermissionNamesByUserId(userId);
        String scope = String.join(" ", new TreeSet<>(scopes));
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("scope", scope)
                .claim("authorities", authorities)
                .claim("client_id", clientId)
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedAccessToken(value, ttl.toSeconds(), scope);
    }

    public record IssuedAccessToken(String value, long expiresInSeconds, String scope) {}
}
```

`OAuthTokenService.java` (versão T2):

```java
package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@Profile("!basic-security")
public class OAuthTokenService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final String dummyHash;

    public OAuthTokenService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    @Transactional
    public TokenResult passwordGrant(SecurityProperties.Client client, String username, String password,
                                     String requestedScope) {
        if (username == null || username.isBlank() || password == null) {
            throw OAuthException.invalidRequest("username and password are required");
        }
        Set<String> scopes = resolveScopes(client, requestedScope);
        Optional<User> user = userRepository.findByEmail(username);
        // Always run BCrypt so unknown e-mails and wrong passwords take the same time.
        boolean matches = passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw OAuthException.invalidGrant("Invalid credentials");
        }
        String userId = user.get().getId();
        return new TokenResult(accessTokenService.issue(userId, client.clientId(), scopes), userId, null);
    }

    static Set<String> resolveScopes(SecurityProperties.Client client, String requested) {
        if (requested == null || requested.isBlank()) {
            return new TreeSet<>(client.scopes());
        }
        Set<String> scopes = new TreeSet<>(Arrays.asList(requested.trim().split("\\s+")));
        if (!client.scopes().containsAll(scopes)) {
            throw OAuthException.invalidScope();
        }
        return scopes;
    }

    public record TokenResult(AccessTokenService.IssuedAccessToken accessToken, String userId, String refreshToken) {}
}
```

`TokenController.java` (versão T2):

```java
package com.nossoteto.security;

import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth/token")
@Profile("!basic-security")
public class TokenController {

    private final ClientAuthenticator clientAuthenticator;
    private final OAuthTokenService tokenService;

    public TokenController(ClientAuthenticator clientAuthenticator, OAuthTokenService tokenService) {
        this.clientAuthenticator = clientAuthenticator;
        this.tokenService = tokenService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form) {
        SecurityProperties.Client client = clientAuthenticator.authenticate(authorization);
        String grantType = form.getFirst("grant_type");
        OAuthTokenService.TokenResult result = switch (grantType == null ? "" : grantType) {
            case "password" -> tokenService.passwordGrant(client, form.getFirst("username"),
                    form.getFirst("password"), form.getFirst("scope"));
            case "" -> throw OAuthException.invalidRequest("grant_type is required");
            default -> throw OAuthException.unsupportedGrantType();
        };
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body(result));
    }

    static Map<String, Object> body(OAuthTokenService.TokenResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", result.accessToken().value());
        body.put("token_type", "Bearer");
        body.put("expires_in", result.accessToken().expiresInSeconds());
        body.put("scope", result.accessToken().scope());
        body.put("user_id", result.userId());
        return body;
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, String>> handle(OAuthException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore());
        if (ex.getStatus().value() == 401) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"nosso-teto\"");
        }
        return builder.body(Map.of("error", ex.getError(), "error_description", ex.getMessage()));
    }
}
```

Nos 7 controllers, trocar o corpo de `extractUserId` por:

```java
    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
```

`AuthController`: remover o método `login` e os imports de `AuthResponse`/`LoginRequest`. `AuthService`: remover `loginUser`, o campo/parâmetro `JwtTokenProvider` e imports órfãos (construtor final: `AuthService(UserRepository, PermissionRepository, PasswordEncoder)`). `AuthServiceTest`: remover os 3 testes de login e o mock do `JwtTokenProvider`.

- [ ] **Step 5: Rodar os testes focados**

Run: `mvn -B test "-Dtest=ClientAuthenticatorTest,OAuthPasswordFlowIntegrationTest,AuthServiceTest,NossoTetoIntegrationTest,ApiErrorHandlingTest,*ControllerTest"`
Expected: PASS.

- [ ] **Step 6: Nível 1 → Crítico → (Nível 3)* → Commit**

```bash
git add -A src/main/java/com/nossoteto/security src/main/java/com/nossoteto/controller src/main/java/com/nossoteto/dto src/main/java/com/nossoteto/service/AuthService.java src/test/java/com/nossoteto pom.xml src/main/resources/application.properties src/test/resources/application.properties
git commit -m "feat: replace custom JWT filter with OAuth2 resource server and password grant token endpoint"
```

(`git add -A <paths>` limitado a esses caminhos para registrar as remoções; conferir `git diff --cached --stat` antes.)

---

### Task 3: Refresh Token Opaco em Cookie, Rotação Atômica, Reuso e Logout

**Gauntlet:** SIM · **Builder:** neon · **PR:** 2
**Crítico foca em:** spec §5.1–5.3; INV-04, INV-05, INV-06, INV-07, INV-08, INV-09, INV-10, INV-18.

**Files:**
- Create: `src/main/java/com/nossoteto/security/RefreshTokenService.java`
- Create: `src/main/java/com/nossoteto/security/RefreshTokenCookieFactory.java`
- Modify: `src/main/java/com/nossoteto/security/OAuthTokenService.java`
- Modify: `src/main/java/com/nossoteto/security/TokenController.java`
- Create: `src/test/java/com/nossoteto/security/RefreshTokenServiceTest.java`
- Create: `src/test/java/com/nossoteto/integration/RefreshTokenFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `RefreshTokenRepository` (T1), `SecurityProperties`, `Clock`, `AccessTokenService`, `OAuthException` (T2).
- Produces:
  - `RefreshTokenService.issue(String userId, String clientId, String scope, String familyIdOrNull): String`
  - `RefreshTokenService.rotate(String rawValue, String clientId): RefreshTokenService.Rotation(String userId, String scope, String refreshToken)`
  - `RefreshTokenService.revoke(String rawValue): void`
  - `RefreshTokenService.sha256Hex(String): String` (static)
  - `RefreshTokenCookieFactory.COOKIE_NAME = "refresh_token"`, `create(String value): ResponseCookie`, `clear(): ResponseCookie`
  - `OAuthTokenService.refreshGrant(Client, String rawRefresh): TokenResult`; `OAuthTokenService.revoke(String rawRefresh): void`; `passwordGrant` agora devolve `refreshToken` não nulo.

- [ ] **Step 1: Testes que falham**

`src/test/java/com/nossoteto/security/RefreshTokenServiceTest.java`:

```java
package com.nossoteto.security;

import com.nossoteto.model.RefreshToken;
import com.nossoteto.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RefreshTokenServiceTest {

    @Autowired private RefreshTokenService service;
    @Autowired private RefreshTokenRepository repository;
    @Autowired private com.nossoteto.service.AuthService authService;

    private String newUser() {
        com.nossoteto.dto.RegisterRequest r = new com.nossoteto.dto.RegisterRequest();
        r.setEmail("rt-" + UUID.randomUUID() + "@nossoteto.com");
        r.setPassword("senha123");
        return authService.registerUser(r).getId();
    }

    @Test
    void onlyTheHashIsPersisted() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        RefreshToken stored = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        assertNotEquals(raw, stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertTrue(raw.length() >= 43);
    }

    @Test
    void rotationIssuesNewTokenInSameFamilyAndKeepsScope() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read", null);
        RefreshTokenService.Rotation rotation = service.rotate(raw, "nosso-teto-web");
        assertNotEquals(raw, rotation.refreshToken());
        assertEquals("read", rotation.scope());
        RefreshToken oldT = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        RefreshToken newT = repository.findByTokenHash(RefreshTokenService.sha256Hex(rotation.refreshToken())).orElseThrow();
        assertEquals(oldT.getFamilyId(), newT.getFamilyId());
        assertNotNull(oldT.getUsedAt());
    }

    @Test
    void reuseRevokesTheWholeFamilyAndThatRevocationIsCommitted() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        String second = service.rotate(raw, "nosso-teto-web").refreshToken();

        OAuthException ex = assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-web"));
        assertEquals("invalid_grant", ex.getError());

        assertNotNull(repository.findByTokenHash(RefreshTokenService.sha256Hex(second)).orElseThrow().getRevokedAt());
        assertThrows(OAuthException.class, () -> service.rotate(second, "nosso-teto-web"));
    }

    @Test
    void unknownBlankOtherClientAndRevokedAreInvalidGrant() {
        assertThrows(OAuthException.class, () -> service.rotate("nope", "nosso-teto-web"));
        assertThrows(OAuthException.class, () -> service.rotate(null, "nosso-teto-web"));
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-mobile"));
        String other = service.issue(newUser(), "nosso-teto-web", "read write", null);
        service.revoke(other);
        assertThrows(OAuthException.class, () -> service.rotate(other, "nosso-teto-web"));
    }

    @Test
    void concurrentRotationOfTheSameTokenSucceedsAtMostOnce() throws Exception {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Callable<Void> task = () -> {
            start.await();
            try { service.rotate(raw, "nosso-teto-web"); ok.incrementAndGet(); }
            // H2 may surface the losing writer as a lock/concurrency exception instead of OAuthException.
            catch (RuntimeException e) { failed.incrementAndGet(); }
            return null;
        };
        Future<Void> a = pool.submit(task);
        Future<Void> b = pool.submit(task);
        start.countDown();
        a.get(10, TimeUnit.SECONDS);
        b.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        // The first writer never waits on a lock, so exactly one success is deterministic (INV-06).
        assertEquals(1, ok.get(), "exactly one rotation must succeed");
        assertEquals(1, failed.get());
    }
}
```

`src/test/java/com/nossoteto/integration/RefreshTokenFlowIntegrationTest.java`:

```java
package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    private String email;

    @BeforeEach
    void register() throws Exception {
        email = "refresh-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"R\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated());
    }

    private MvcResult passwordLogin() throws Exception {
        return mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(Cookie cookie) throws Exception {
        var req = post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token");
        if (cookie != null) req.cookie(cookie);
        return mockMvc.perform(req);
    }

    @Test
    void passwordGrantSetsHardenedCookieAndNeverReturnsRefreshInBody() throws Exception {
        MvcResult r = passwordLogin();
        String setCookie = r.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("refresh_token="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Path=/oauth/token"));
        assertFalse(r.getResponse().getContentAsString().contains("refresh"));
    }

    @Test
    void refreshRotatesAndOldCookieIsReuse() throws Exception {
        Cookie first = passwordLogin().getResponse().getCookie("refresh_token");
        MvcResult rotated = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", not(emptyOrNullString()))).andReturn();
        Cookie second = rotated.getResponse().getCookie("refresh_token");
        assertNotEquals(first.getValue(), second.getValue());

        refresh(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        // family revoked: the legitimately rotated token is dead too
        refresh(second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void refreshWithoutCookieIsInvalidGrant() throws Exception {
        refresh(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void logoutRevokesFamilyAndClearsCookie() throws Exception {
        Cookie cookie = passwordLogin().getResponse().getCookie("refresh_token");
        MvcResult out = mockMvc.perform(delete("/oauth/token/revoke")
                        .with(httpBasic("nosso-teto-web", "web-test-secret")).cookie(cookie))
                .andExpect(status().isNoContent()).andReturn();
        assertTrue(out.getResponse().getHeader("Set-Cookie").contains("Max-Age=0"));
        refresh(cookie).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void logoutIsIdempotentAndRequiresClientAuth() throws Exception {
        mockMvc.perform(delete("/oauth/token/revoke").with(httpBasic("nosso-teto-web", "web-test-secret")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/oauth/token/revoke"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -B test "-Dtest=RefreshTokenServiceTest,RefreshTokenFlowIntegrationTest"`
Expected: FAIL de compilação (`RefreshTokenService` inexistente).

- [ ] **Step 3: Implementação**

`RefreshTokenService.java`:

```java
package com.nossoteto.security;

import com.nossoteto.model.RefreshToken;
import com.nossoteto.repository.RefreshTokenRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Profile("!basic-security")
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.ttl = properties.refreshTokenTtl();
        this.clock = clock;
    }

    @Transactional
    public String issue(String userId, String clientId, String scope, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now(clock);

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID().toString());
        token.setTokenHash(sha256Hex(raw));
        token.setFamilyId(familyId != null ? familyId : UUID.randomUUID().toString());
        token.setUserId(userId);
        token.setClientId(clientId);
        token.setScope(scope);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return raw;
    }

    // noRollbackFor: a detected reuse must keep the family revocation even though we throw (INV-07).
    @Transactional(noRollbackFor = OAuthException.class)
    public Rotation rotate(String raw, String clientId) {
        if (raw == null || raw.isBlank()) {
            throw OAuthException.invalidGrant("Refresh token missing");
        }
        RefreshToken current = repository.findByTokenHash(sha256Hex(raw))
                .orElseThrow(() -> OAuthException.invalidGrant("Invalid refresh token"));
        LocalDateTime now = LocalDateTime.now(clock);

        if (current.getRevokedAt() != null) {
            throw OAuthException.invalidGrant("Refresh token revoked");
        }
        if (current.getUsedAt() != null) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw OAuthException.invalidGrant("Refresh token reuse detected");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw OAuthException.invalidGrant("Refresh token expired");
        }
        if (!current.getClientId().equals(clientId)) {
            throw OAuthException.invalidGrant("Refresh token was issued to another client");
        }
        // Atomic compare-and-set: only one concurrent caller can flip used_at (INV-06).
        if (repository.markUsed(current.getId(), now) != 1) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw OAuthException.invalidGrant("Refresh token reuse detected");
        }
        String next = issue(current.getUserId(), current.getClientId(), current.getScope(), current.getFamilyId());
        return new Rotation(current.getUserId(), current.getScope(), next);
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256Hex(raw))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), LocalDateTime.now(clock)));
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Rotation(String userId, String scope, String refreshToken) {}
}
```

`RefreshTokenCookieFactory.java`:

```java
package com.nossoteto.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String PATH = "/oauth/token";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookieFactory(SecurityProperties properties) {
        this.secure = properties.cookie().secure();
        this.maxAge = properties.refreshTokenTtl();
    }

    public ResponseCookie create(String value) {
        return base(value).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
```

`OAuthTokenService` — injetar `RefreshTokenService refreshTokenService` no construtor; no fim de `passwordGrant`:

```java
        String userId = user.get().getId();
        String scope = String.join(" ", scopes);
        String refresh = refreshTokenService.issue(userId, client.clientId(), scope, null);
        return new TokenResult(accessTokenService.issue(userId, client.clientId(), scopes), userId, refresh);
```

e adicionar:

```java
    @Transactional(noRollbackFor = OAuthException.class)
    public TokenResult refreshGrant(SecurityProperties.Client client, String rawRefresh) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefresh, client.clientId());
        Set<String> scopes = new TreeSet<>(Arrays.asList(rotation.scope().split(" ")));
        return new TokenResult(accessTokenService.issue(rotation.userId(), client.clientId(), scopes),
                rotation.userId(), rotation.refreshToken());
    }

    public void revoke(String rawRefresh) {
        refreshTokenService.revoke(rawRefresh);
    }
```

`TokenController` — injetar `RefreshTokenCookieFactory cookieFactory`; assinatura do `token` ganha `@CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshCookie`; o `switch` ganha `case "refresh_token" -> tokenService.refreshGrant(client, refreshCookie);`; o retorno passa a:

```java
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(body(result));
```

e adicionar:

```java
    @DeleteMapping("/revoke")
    public ResponseEntity<Void> revoke(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshCookie) {
        clientAuthenticator.authenticate(authorization);
        tokenService.revoke(refreshCookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }
```

(imports `org.springframework.web.bind.annotation.CookieValue`, `DeleteMapping`.)

- [ ] **Step 4: Rodar os testes focados**

Run: `mvn -B test "-Dtest=RefreshTokenServiceTest,RefreshTokenFlowIntegrationTest,OAuthPasswordFlowIntegrationTest"`
Expected: PASS.

- [ ] **Step 5: Nível 1 → Crítico → (Nível 3)* → Commit**

```bash
git add src/main/java/com/nossoteto/security/RefreshTokenService.java src/main/java/com/nossoteto/security/RefreshTokenCookieFactory.java src/main/java/com/nossoteto/security/OAuthTokenService.java src/main/java/com/nossoteto/security/TokenController.java src/test/java/com/nossoteto/security/RefreshTokenServiceTest.java src/test/java/com/nossoteto/integration/RefreshTokenFlowIntegrationTest.java
git commit -m "feat: add rotating refresh token in HttpOnly cookie with reuse detection and logout"
```

---

### Task 4: Profile `basic-security` (HTTP Basic contra o banco)

**Gauntlet:** não · **Builder:** hydrogen · **PR:** 2

**Files:**
- Create: `src/main/java/com/nossoteto/security/BasicSecurityConfig.java`
- Create: `src/main/java/com/nossoteto/security/DatabaseUserDetailsService.java`
- Create: `src/test/java/com/nossoteto/security/DatabaseUserDetailsServiceTest.java`
- Create: `src/test/java/com/nossoteto/integration/BasicSecurityProfileIntegrationTest.java`

**Interfaces:**
- Consumes: `JsonAuthHandlers`, `CommonSecurityConfig` (T2), `UserRepository.findPermissionNamesByUserId` (T1).
- Produces: `DatabaseUserDetailsService.loadUserByUsername(String email): UserDetails` com `getUsername()` = user id.

- [ ] **Step 1: Testes que falham**

`DatabaseUserDetailsServiceTest.java`:

```java
package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(repo);

    @Test
    void usernameIsTheUserIdAndAuthoritiesIncludePermissionsAndBothScopes() {
        User u = new User();
        u.setId("user-42");
        u.setEmail("a@b.com");
        u.setPasswordHash("$2a$hash");
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(repo.findPermissionNamesByUserId("user-42")).thenReturn(List.of("ROLE_MANAGE_PROFILE"));

        UserDetails details = service.loadUserByUsername("a@b.com");

        assertEquals("user-42", details.getUsername());
        assertEquals("$2a$hash", details.getPassword());
        Set<String> auths = details.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_MANAGE_PROFILE", "SCOPE_read", "SCOPE_write"), auths);
    }

    @Test
    void unknownEmailThrows() {
        when(repo.findByEmail("x@y.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("x@y.com"));
    }
}
```

`BasicSecurityProfileIntegrationTest.java`:

```java
package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("basic-security")
class BasicSecurityProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void basicCredentialsAuthenticateAndPrincipalIsTheUserId() throws Exception {
        String email = "basic-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"B\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/users/me").with(httpBasic(email, "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/users/me").with(httpBasic(email, "errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void tokenEndpointDoesNotExistInBasicProfile() throws Exception {
        mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -B test "-Dtest=DatabaseUserDetailsServiceTest,BasicSecurityProfileIntegrationTest"`
Expected: FAIL de compilação.

- [ ] **Step 3: Implementação**

`DatabaseUserDetailsService.java`:

```java
package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("basic-security")
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        List<GrantedAuthority> authorities = new ArrayList<>();
        userRepository.findPermissionNamesByUserId(user.getId())
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        // Basic auth has no OAuth client, so it carries full scope.
        authorities.add(new SimpleGrantedAuthority("SCOPE_read"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_write"));
        // username = user id, because every service keys on the user id (INV-11/INV-12).
        return org.springframework.security.core.userdetails.User.withUsername(user.getId())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}
```

`BasicSecurityConfig.java`:

```java
package com.nossoteto.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("basic-security")
public class BasicSecurityConfig {

    @Bean
    public SecurityFilterChain basicChain(HttpSecurity http, JsonAuthHandlers handlers,
                                          DatabaseUserDetailsService userDetailsService) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(userDetailsService)
                .httpBasic(b -> b.authenticationEntryPoint(handlers))
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
```

- [ ] **Step 4: Rodar os testes focados**

Run: `mvn -B test "-Dtest=DatabaseUserDetailsServiceTest,BasicSecurityProfileIntegrationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nossoteto/security/BasicSecurityConfig.java src/main/java/com/nossoteto/security/DatabaseUserDetailsService.java src/test/java/com/nossoteto/security/DatabaseUserDetailsServiceTest.java src/test/java/com/nossoteto/integration/BasicSecurityProfileIntegrationTest.java
git commit -m "feat: add basic-security profile with database-backed HTTP Basic authentication"
```

---

### Task 5: Matriz de Permissões (`@PreAuthorize`) + Administração de Usuários

**Gauntlet:** SIM · **Builder:** neon · **PR:** 2
**Crítico foca em:** spec §4 (linha a linha), INV-01, INV-02, INV-05, INV-13, INV-17, INV-18.

**Files:**
- Modify: `ChoreController`, `DashboardController`, `ExpenseController`, `GroceryController`, `HouseController`, `MatchController`, `UserController` (somente anotações + import)
- Create: `src/main/java/com/nossoteto/controller/AdminController.java`
- Create: `src/main/java/com/nossoteto/service/AdminService.java`
- Create: `src/main/java/com/nossoteto/dto/AdminUserResponse.java`
- Create: `src/main/java/com/nossoteto/dto/UpdatePermissionsRequest.java`
- Create: `src/main/java/com/nossoteto/security/AdminBootstrap.java`
- Modify: `src/main/resources/application.properties`, `src/test/resources/application.properties`
- Create: `src/test/java/com/nossoteto/service/AdminServiceTest.java`
- Create: `src/test/java/com/nossoteto/integration/AuthorizationMatrixIntegrationTest.java`
- Create: `src/test/java/com/nossoteto/integration/AdminPermissionFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `Permissions`, `PermissionRepository`, `UserRepository` (T1); tokens via `/oauth/token` (T2/T3).
- Produces: `AdminService.listUsers(): List<AdminUserResponse>`; `AdminService.replacePermissions(String actorId, String targetId, List<String> names): AdminUserResponse`; `AdminUserResponse(String id, String name, String email, String houseId, List<String> permissions)` (record); `UpdatePermissionsRequest(List<String> permissions)` (record).

- [ ] **Step 1: Propriedades do admin**

`src/main/resources/application.properties`:

```properties
app.admin.email=${ADMIN_EMAIL:admin@nossoteto.local}
app.admin.password=${ADMIN_PASSWORD:}
```

`src/test/resources/application.properties`:

```properties
app.admin.email=admin@test.local
app.admin.password=admin-test-pass
```

- [ ] **Step 2: Testes que falham**

`AuthorizationMatrixIntegrationTest.java` — matriz completa da spec §4:

```java
package com.nossoteto.integration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixIntegrationTest {

    private static final String DENIED = "Insufficient permission";

    @Autowired private MockMvc mockMvc;

    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of("GET", "/api/users/me", "ROLE_MANAGE_PROFILE", "read"),
                Arguments.of("PUT", "/api/users/me/profile", "ROLE_MANAGE_PROFILE", "write"),
                Arguments.of("GET", "/api/houses", "ROLE_SEARCH_HOUSES", "read"),
                Arguments.of("GET", "/api/houses/h1", "ROLE_SEARCH_HOUSES", "read"),
                Arguments.of("POST", "/api/houses", "ROLE_MANAGE_HOUSE", "write"),
                Arguments.of("PUT", "/api/houses/h1", "ROLE_MANAGE_HOUSE", "write"),
                Arguments.of("DELETE", "/api/houses/h1/members/u2", "ROLE_MANAGE_HOUSE", "write"),
                Arguments.of("POST", "/api/matches/requests", "ROLE_MATCH", "write"),
                Arguments.of("GET", "/api/houses/h1/matches", "ROLE_MATCH", "read"),
                Arguments.of("POST", "/api/matches/requests/r1/vote", "ROLE_MATCH", "write"),
                Arguments.of("POST", "/api/matches/requests/cancel-others", "ROLE_MATCH", "write"),
                Arguments.of("GET", "/api/houses/h1/expenses", "ROLE_MANAGE_EXPENSES", "read"),
                Arguments.of("GET", "/api/houses/h1/expenses/e1/attachment", "ROLE_MANAGE_EXPENSES", "read"),
                Arguments.of("POST", "/api/houses/h1/expenses", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("POST", "/api/houses/h1/expenses/e1/propose-split", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("POST", "/api/houses/h1/expenses/e1/vote-split", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("PUT", "/api/houses/h1/expenses/e1/pay", "ROLE_MANAGE_EXPENSES", "write"),
                Arguments.of("GET", "/api/houses/h1/chores", "ROLE_MANAGE_CHORES", "read"),
                Arguments.of("POST", "/api/houses/h1/chores", "ROLE_MANAGE_CHORES", "write"),
                Arguments.of("PUT", "/api/houses/h1/chores/c1/complete", "ROLE_MANAGE_CHORES", "write"),
                Arguments.of("POST", "/api/houses/h1/chores/rotate", "ROLE_MANAGE_CHORES", "write"),
                Arguments.of("GET", "/api/houses/h1/groceries", "ROLE_MANAGE_GROCERIES", "read"),
                Arguments.of("POST", "/api/houses/h1/groceries", "ROLE_MANAGE_GROCERIES", "write"),
                Arguments.of("PUT", "/api/houses/h1/groceries/g1/assign", "ROLE_MANAGE_GROCERIES", "write"),
                Arguments.of("PUT", "/api/houses/h1/groceries/g1/purchase", "ROLE_MANAGE_GROCERIES", "write"),
                Arguments.of("GET", "/api/houses/h1/dashboard", "ROLE_VIEW_DASHBOARD", "read"),
                Arguments.of("GET", "/api/admin/users", "ROLE_ADMIN_USERS", "read"),
                Arguments.of("PUT", "/api/admin/users/u1/permissions", "ROLE_ADMIN_USERS", "write"));
    }

    private MockHttpServletRequestBuilder req(String method, String url) {
        return request(HttpMethod.valueOf(method), url).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest(name = "{0} {1} sem permissao -> 403")
    @MethodSource("matrix")
    void withoutPermissionIsForbidden(String method, String url, String permission, String scope) throws Exception {
        mockMvc.perform(req(method, url).with(jwt().jwt(j -> j.subject("matrix-user"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_read"), new SimpleGrantedAuthority("SCOPE_write"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DENIED));
    }

    @ParameterizedTest(name = "{0} {1} scope errado -> 403")
    @MethodSource("matrix")
    void withWrongScopeIsForbidden(String method, String url, String permission, String scope) throws Exception {
        String wrong = "read".equals(scope) ? "SCOPE_write" : "SCOPE_read";
        mockMvc.perform(req(method, url).with(jwt().jwt(j -> j.subject("matrix-user"))
                        .authorities(new SimpleGrantedAuthority(permission), new SimpleGrantedAuthority(wrong))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DENIED));
    }

    @ParameterizedTest(name = "{0} {1} permissao+scope -> passa da camada de permissao")
    @MethodSource("matrix")
    void withPermissionAndScopePassesTheAuthorityLayer(String method, String url, String permission, String scope) throws Exception {
        MvcResult r = mockMvc.perform(req(method, url).with(jwt().jwt(j -> j.subject("matrix-user"))
                        .authorities(new SimpleGrantedAuthority(permission), new SimpleGrantedAuthority("SCOPE_" + scope))))
                .andReturn();
        // Downstream may answer 400/403/404 (house checks, unknown user "matrix-user"), but never the authority-layer denial.
        assertFalse(r.getResponse().getContentAsString().contains(DENIED), method + " " + url);
    }

    @org.junit.jupiter.api.Test
    void leaveHouseNeedsOnlyWriteScope() throws Exception {
        MvcResult r = mockMvc.perform(req("POST", "/api/houses/h1/leave")
                        .with(jwt().jwt(j -> j.subject("matrix-user")).authorities(new SimpleGrantedAuthority("SCOPE_write"))))
                .andReturn();
        assertFalse(r.getResponse().getContentAsString().contains(DENIED));
        mockMvc.perform(req("POST", "/api/houses/h1/leave")
                        .with(jwt().jwt(j -> j.subject("matrix-user")).authorities(new SimpleGrantedAuthority("SCOPE_read"))))
                .andExpect(status().isForbidden());
    }
}
```

Se algum caso do terceiro teste revelar 5xx por bug pré-existente do CP2 (ex.: NPE com corpo `{}`), **não corrigir nesta tarefa**: registrar no relatório para o Coordenador abrir issue separada (fora do ownership).

`AdminServiceTest.java`:

```java
package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final AdminService service = new AdminService(users, permissions);

    private User user(String id) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@x.com");
        return u;
    }

    @Test
    void replacesTheWholePermissionSet() {
        User target = user("t1");
        Permission match = new Permission(4, "ROLE_MATCH", "");
        when(users.findById("t1")).thenReturn(Optional.of(target));
        when(permissions.findByNameIn(Set.of("ROLE_MATCH"))).thenReturn(List.of(match));
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findPermissionNamesByUserId("t1")).thenReturn(List.of("ROLE_MATCH"));

        var response = service.replacePermissions("admin", "t1", List.of("ROLE_MATCH"));

        assertEquals(Set.of(match), target.getPermissions());
        assertEquals(List.of("ROLE_MATCH"), response.permissions());
    }

    @Test
    void unknownPermissionIsRejected() {
        when(users.findById("t1")).thenReturn(Optional.of(user("t1")));
        when(permissions.findByNameIn(Set.of("ROLE_GOD"))).thenReturn(List.of());
        assertThrows(BusinessRuleException.class, () -> service.replacePermissions("admin", "t1", List.of("ROLE_GOD")));
    }

    @Test
    void nullListIsRejected() {
        assertThrows(BusinessRuleException.class, () -> service.replacePermissions("admin", "t1", null));
    }

    @Test
    void adminCannotDropOwnAdminPermission() {
        when(users.findById("admin")).thenReturn(Optional.of(user("admin")));
        assertThrows(BusinessRuleException.class,
                () -> service.replacePermissions("admin", "admin", List.of("ROLE_MATCH")));
    }

    @Test
    void unknownUserIs404() {
        when(users.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.replacePermissions("admin", "ghost", List.of()));
    }
}
```

`AdminPermissionFlowIntegrationTest.java` (fluxo da demo):

```java
package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPermissionFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", password))
                .andExpect(status().isOk()).andReturn();
    }

    private String access(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("access_token").asText();
    }

    @Test
    void adminRevokesPermissionAndItAppliesOnNextRefresh() throws Exception {
        String email = "victim-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"V\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();

        MvcResult userLogin = login(email, "senha123");
        Cookie userCookie = userLogin.getResponse().getCookie("refresh_token");
        String adminToken = access(login("admin@test.local", "admin-test-pass"));

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/users/" + userId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"ROLE_MANAGE_PROFILE\",\"ROLE_SEARCH_HOUSES\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("ROLE_MANAGE_HOUSE"))));

        MvcResult refreshed = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token")
                        .cookie(userCookie))
                .andExpect(status().isOk()).andReturn();

        mockMvc.perform(post("/api/houses").header("Authorization", "Bearer " + access(refreshed))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Insufficient permission"));
    }

    @Test
    void regularUserCannotUseAdminEndpoints() throws Exception {
        String email = "plain-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"P\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + access(login(email, "senha123"))))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -B test "-Dtest=AuthorizationMatrixIntegrationTest,AdminServiceTest,AdminPermissionFlowIntegrationTest"`
Expected: FAIL (compilação de `AdminService`; depois 200/404 onde se espera 403).

- [ ] **Step 4: Implementação**

Anotações nos controllers (import `org.springframework.security.access.prepost.PreAuthorize`). Usar exatamente estas expressões:

| Controller.método | `@PreAuthorize(...)` |
|---|---|
| `UserController.getCurrentUser` | `"hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_read')"` |
| `UserController.updateProfile` | `"hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_write')"` |
| `HouseController.listOpenHouses`, `getHouseDetails` | `"hasAuthority('ROLE_SEARCH_HOUSES') and hasAuthority('SCOPE_read')"` |
| `HouseController.createHouse`, `updateHouse`, `removeMember` | `"hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_write')"` |
| `HouseController.leaveHouse` | `"hasAuthority('SCOPE_write')"` |
| `MatchController.createMatchRequest`, `voteOnMatchRequest`, `cancelOtherPendingRequests` | `"hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_write')"` |
| `MatchController.listHouseMatchRequests` | `"hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_read')"` |
| `ExpenseController.listExpenses`, `getAttachment` | `"hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_read')"` |
| `ExpenseController.createExpense`, `proposeCustomSplit`, `voteOnCustomSplit`, `markSplitAsPaid` | `"hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')"` |
| `ChoreController.listChores` | `"hasAuthority('ROLE_MANAGE_CHORES') and hasAuthority('SCOPE_read')"` |
| `ChoreController.createChore`, `completeChore`, `rotateHouseChores` | `"hasAuthority('ROLE_MANAGE_CHORES') and hasAuthority('SCOPE_write')"` |
| `GroceryController.listGroceries` | `"hasAuthority('ROLE_MANAGE_GROCERIES') and hasAuthority('SCOPE_read')"` |
| `GroceryController.createGrocery`, `assignGrocery`, `markAsPurchased` | `"hasAuthority('ROLE_MANAGE_GROCERIES') and hasAuthority('SCOPE_write')"` |
| `DashboardController.getHouseDashboard` | `"hasAuthority('ROLE_VIEW_DASHBOARD') and hasAuthority('SCOPE_read')"` |

`AdminUserResponse.java`:

```java
package com.nossoteto.dto;

import java.util.List;

public record AdminUserResponse(String id, String name, String email, String houseId, List<String> permissions) {}
```

`UpdatePermissionsRequest.java`:

```java
package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record UpdatePermissionsRequest(@JsonAlias("permissoes") List<String> permissions) {}
```

`AdminService.java`:

```java
package com.nossoteto.service;

import com.nossoteto.dto.AdminUserResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public AdminService(UserRepository userRepository, PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminUserResponse replacePermissions(String actorId, String targetId, List<String> names) {
        if (names == null) {
            throw new BusinessRuleException("permissions is required");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));
        Set<String> requested = names.stream().collect(Collectors.toSet());
        if (targetId.equals(actorId) && !requested.contains(Permissions.ADMIN_USERS)) {
            throw new BusinessRuleException("An administrator cannot remove their own ROLE_ADMIN_USERS");
        }
        List<Permission> found = requested.isEmpty() ? List.of() : permissionRepository.findByNameIn(requested);
        if (found.size() != requested.size()) {
            Set<String> known = found.stream().map(Permission::getName).collect(Collectors.toSet());
            String unknown = requested.stream().filter(n -> !known.contains(n)).sorted().collect(Collectors.joining(", "));
            throw new BusinessRuleException("Unknown permission: " + unknown);
        }
        target.setPermissions(new HashSet<>(found));
        userRepository.save(target);
        return toResponse(target);
    }

    private AdminUserResponse toResponse(User user) {
        List<String> permissions = userRepository.findPermissionNamesByUserId(user.getId()).stream().sorted().toList();
        return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getHouseId(), permissions);
    }
}
```

`AdminController.java`:

```java
package com.nossoteto.controller;

import com.nossoteto.dto.AdminUserResponse;
import com.nossoteto.dto.UpdatePermissionsRequest;
import com.nossoteto.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USERS') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USERS') and hasAuthority('SCOPE_write')")
    public ResponseEntity<AdminUserResponse> replacePermissions(Authentication authentication,
                                                                @PathVariable String id,
                                                                @RequestBody UpdatePermissionsRequest request) {
        return ResponseEntity.ok(adminService.replacePermissions(authentication.getName(), id, request.permissions()));
    }
}
```

`AdminBootstrap.java`:

```java
package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository, PermissionRepository permissionRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("ADMIN_PASSWORD not set: skipping platform admin bootstrap");
            return;
        }
        User admin = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setId(UUID.randomUUID().toString());
            u.setName("Administrador");
            u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode(password));
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            return u;
        });
        // Idempotent: never resets an existing password, only guarantees the full permission set.
        admin.setPermissions(new HashSet<>(permissionRepository.findAll()));
        userRepository.save(admin);
        log.info("Platform admin ensured for {}", email);
    }
}
```

- [ ] **Step 5: Rodar os testes focados + golden path**

Run: `mvn -B test "-Dtest=AuthorizationMatrixIntegrationTest,AdminServiceTest,AdminPermissionFlowIntegrationTest,NossoTetoIntegrationTest,ApiErrorHandlingTest"`
Expected: PASS.

- [ ] **Step 6: Nível 1 → Crítico → (Nível 3)* → Commit**

```bash
git add src/main/java/com/nossoteto/controller src/main/java/com/nossoteto/service/AdminService.java src/main/java/com/nossoteto/dto/AdminUserResponse.java src/main/java/com/nossoteto/dto/UpdatePermissionsRequest.java src/main/java/com/nossoteto/security/AdminBootstrap.java src/main/resources/application.properties src/test/resources/application.properties src/test/java/com/nossoteto/service/AdminServiceTest.java src/test/java/com/nossoteto/integration/AuthorizationMatrixIntegrationTest.java src/test/java/com/nossoteto/integration/AdminPermissionFlowIntegrationTest.java
git commit -m "feat: enforce permission matrix with method security and add user permission administration"
```

**Fim do PR 2:** revisão completa do Coordenador (`requesting-code-review`) → `git push -u origin feat/checkpoint-3-auth-core` → `gh pr create` → CI verde → merge com autorização.

---

### Task 6: CORS com Credenciais

**Gauntlet:** não · **Builder:** hydrogen · **PR:** 3

**Files:**
- Modify: `src/main/java/com/nossoteto/security/CommonSecurityConfig.java`
- Modify: `src/main/resources/application.properties`, `src/test/resources/application.properties`
- Create: `src/test/java/com/nossoteto/integration/CorsIntegrationTest.java`

**Interfaces:**
- Produces: bean `CorsConfigurationSource corsConfigurationSource` (usado pelo `.cors(withDefaults())` de todas as chains).

- [ ] **Step 1: Propriedades**

main: `app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}` · test: `app.cors.allowed-origins=http://localhost:5173`

- [ ] **Step 2: Teste que falha** — `CorsIntegrationTest.java`:

```java
package com.nossoteto.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void preflightFromAllowedOriginOnTokenEndpointAllowsCredentials() throws Exception {
        mockMvc.perform(options("/oauth/token")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflightFromAllowedOriginOnApi() throws Exception {
        mockMvc.perform(options("/api/users/me")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/users/me")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
```

Run: `mvn -B test "-Dtest=CorsIntegrationTest"` → Expected: FAIL (sem `Access-Control-Allow-Origin`).

- [ ] **Step 3: Implementação** — em `CommonSecurityConfig` adicionar:

```java
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins); // explicit list: '*' is illegal with credentials (INV-16)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
```

(imports `org.springframework.beans.factory.annotation.Value`, `org.springframework.web.cors.CorsConfiguration`, `CorsConfigurationSource`, `UrlBasedCorsConfigurationSource`, `java.util.List`.)

- [ ] **Step 4:** Run `mvn -B test "-Dtest=CorsIntegrationTest,BasicSecurityProfileIntegrationTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/nossoteto/security/CommonSecurityConfig.java src/main/resources/application.properties src/test/resources/application.properties src/test/java/com/nossoteto/integration/CorsIntegrationTest.java
git commit -m "feat: configure credentialed CORS for the React frontend origin"
```

---

### Task 7: Criptografia AES-256-GCM de `allergies` e `bio`

**Gauntlet:** SIM · **Builder:** neon · **PR:** 3
**Crítico foca em:** spec §3.2; INV-14, INV-15, INV-18 (IV nunca reutilizado, tag verificada, chave validada, sem vazamento de texto cifrado/claro em logs).

**Files:**
- Create: `src/main/resources/db/migration/V4__Widen_encrypted_profile_columns.sql`
- Create: `src/main/java/com/nossoteto/security/crypto/AesGcmFieldCipher.java`
- Create: `src/main/java/com/nossoteto/security/crypto/FieldCipherHolder.java`
- Create: `src/main/java/com/nossoteto/security/crypto/FieldCipherConfig.java`
- Create: `src/main/java/com/nossoteto/security/crypto/EncryptedStringConverter.java`
- Modify: `src/main/java/com/nossoteto/model/UserProfile.java`
- Modify: `src/main/resources/application.properties`, `src/test/resources/application.properties`
- Create: `src/test/java/com/nossoteto/security/crypto/AesGcmFieldCipherTest.java`
- Create: `src/test/java/com/nossoteto/security/crypto/EncryptedStringConverterTest.java`
- Create: `src/test/java/com/nossoteto/integration/ProfileEncryptionIntegrationTest.java`

**Interfaces:**
- Produces: `AesGcmFieldCipher(byte[] key)` (exige 32 bytes); `encrypt(String): String` (`v1:`…); `decrypt(String): String`; `FieldCipherHolder.set(AesGcmFieldCipher)`, `FieldCipherHolder.get()`; `EncryptedStringConverter` (sem `autoApply`).

> Decisão de design para o Crítico: o converter usa um holder estático porque o `MySqlSchemaCompatibilityTest` constrói o metadata do Hibernate sem Spring; o holder é preenchido no startup pelo `FieldCipherConfig` e falha explicitamente se usado antes (sem fallback silencioso).

- [ ] **Step 1: Migration e propriedades**

`V4__Widen_encrypted_profile_columns.sql`:

```sql
ALTER TABLE user_profiles MODIFY COLUMN allergies TEXT NULL;
```

main: `app.crypto.key=${APP_CRYPTO_KEY}` · test: `app.crypto.key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=`

`UserProfile`: em `allergies` usar `@Column(name = "allergies", columnDefinition = "TEXT")` + `@Convert(converter = EncryptedStringConverter.class)`; em `bio` adicionar `@Convert(converter = EncryptedStringConverter.class)` (import `jakarta.persistence.Convert`).

- [ ] **Step 2: Testes que falham**

`AesGcmFieldCipherTest.java`:

```java
package com.nossoteto.security.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmFieldCipherTest {

    private final AesGcmFieldCipher cipher =
            new AesGcmFieldCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void roundTripWithVersionPrefix() {
        String enc = cipher.encrypt("Alergia a amendoim");
        assertTrue(enc.startsWith("v1:"));
        assertFalse(enc.contains("amendoim"));
        assertEquals("Alergia a amendoim", cipher.decrypt(enc));
    }

    @Test
    void sameInputYieldsDifferentCiphertextBecauseIvIsRandom() {
        assertNotEquals(cipher.encrypt("x"), cipher.encrypt("x"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String enc = cipher.encrypt("segredo");
        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        raw[raw.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        AesGcmFieldCipher other = new AesGcmFieldCipher("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> other.decrypt(cipher.encrypt("segredo")));
    }

    @Test
    void keyMustBe32Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmFieldCipher(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new AesGcmFieldCipher(null));
    }

    @Test
    void truncatedPayloadIsRejected() {
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:AAAA"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:%%%"));
    }
}
```

`EncryptedStringConverterTest.java`:

```java
package com.nossoteto.security.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter();

    @BeforeEach
    void key() {
        FieldCipherHolder.set(new AesGcmFieldCipher("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void nullsPassThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void writesEncryptedAndReadsBack() {
        String db = converter.convertToDatabaseColumn("bio secreta");
        assertTrue(db.startsWith("v1:"));
        assertEquals("bio secreta", converter.convertToEntityAttribute(db));
    }

    @Test
    void legacyPlaintextIsReadAsIs() {
        assertEquals("Nenhuma", converter.convertToEntityAttribute("Nenhuma"));
    }
}
```

`ProfileEncryptionIntegrationTest.java`:

```java
package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileEncryptionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void sensitiveFieldsAreEncryptedAtRestAndPlainThroughTheApi() throws Exception {
        String email = "crypto-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"C\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();
        MvcResult login = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("access_token").asText();

        String longAllergies = "Amendoim, ".repeat(40);
        mockMvc.perform(put("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alergias\":\"" + longAllergies + "\",\"biografia\":\"Gosto de jazz\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("SELECT allergies, bio FROM user_profiles WHERE user_id = ?", userId);
        assertTrue(row.get("allergies").toString().startsWith("v1:"));
        assertFalse(row.get("allergies").toString().contains("Amendoim"));
        assertTrue(row.get("bio").toString().startsWith("v1:"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gosto de jazz")));

        jdbc.update("UPDATE user_profiles SET bio = ? WHERE user_id = ?", "texto legado", userId);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("texto legado")));
    }
}
```

Run: `mvn -B test "-Dtest=AesGcmFieldCipherTest,EncryptedStringConverterTest,ProfileEncryptionIntegrationTest"` → Expected: FAIL de compilação.

- [ ] **Step 3: Implementação**

`AesGcmFieldCipher.java`:

```java
package com.nossoteto.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmFieldCipher {

    public static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public AesGcmFieldCipher(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be exactly 32 bytes");
        }
        this.key = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv); // fresh IV per encryption: never reuse an IV with the same key
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encrypted field is not valid base64");
        }
        if (raw.length < IV_BYTES + TAG_BITS / 8) {
            throw new IllegalStateException("Encrypted field is truncated");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Includes AEADBadTagException: tampered data or wrong key. Never return garbage.
            throw new IllegalStateException("Encrypted field failed authentication");
        }
    }
}
```

`FieldCipherHolder.java`:

```java
package com.nossoteto.security.crypto;

public final class FieldCipherHolder {

    private static volatile AesGcmFieldCipher cipher;

    private FieldCipherHolder() {}

    public static void set(AesGcmFieldCipher value) {
        cipher = value;
    }

    public static AesGcmFieldCipher get() {
        AesGcmFieldCipher current = cipher;
        if (current == null) {
            throw new IllegalStateException("Field cipher not initialized (APP_CRYPTO_KEY)");
        }
        return current;
    }
}
```

`FieldCipherConfig.java`:

```java
package com.nossoteto.security.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class FieldCipherConfig {

    @Bean
    public AesGcmFieldCipher fieldCipher(@Value("${app.crypto.key}") String base64Key) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) must be base64");
        }
        if (key.length != 32) {
            throw new IllegalStateException("app.crypto.key (APP_CRYPTO_KEY) must decode to 32 bytes");
        }
        AesGcmFieldCipher cipher = new AesGcmFieldCipher(key);
        FieldCipherHolder.set(cipher);
        return cipher;
    }
}
```

`EncryptedStringConverter.java`:

```java
package com.nossoteto.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : FieldCipherHolder.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Rows written before encryption have no prefix: read as legacy plaintext, re-encrypted on next save.
        return dbData.startsWith(AesGcmFieldCipher.PREFIX) ? FieldCipherHolder.get().decrypt(dbData) : dbData;
    }
}
```

Garantir que o `EntityManagerFactory` só seja usado depois do `FieldCipherConfig`: anotar `FieldCipherConfig.fieldCipher` não basta — adicionar ao `AdminBootstrap` (único uso de JPA no startup) uma dependência de construtor `AesGcmFieldCipher fieldCipher` (não usada além de forçar a ordem) **ou** `@DependsOn("fieldCipher")` na classe `AdminBootstrap`. Usar `@DependsOn("fieldCipher")` e incluir `AdminBootstrap.java` no ownership desta tarefa.

- [ ] **Step 4:** Run `mvn -B test "-Dtest=AesGcmFieldCipherTest,EncryptedStringConverterTest,ProfileEncryptionIntegrationTest,MySqlSchemaCompatibilityTest,UserServiceTest,UserControllerTest"` → PASS.

- [ ] **Step 5: Nível 1 → Crítico → (Nível 3)* → Commit**

```bash
git add src/main/resources/db/migration/V4__Widen_encrypted_profile_columns.sql src/main/java/com/nossoteto/security/crypto src/main/java/com/nossoteto/model/UserProfile.java src/main/java/com/nossoteto/security/AdminBootstrap.java src/main/resources/application.properties src/test/resources/application.properties src/test/java/com/nossoteto/security/crypto src/test/java/com/nossoteto/integration/ProfileEncryptionIntegrationTest.java
git commit -m "feat: encrypt profile allergies and bio at rest with AES-256-GCM"
```

---

### Task 8: Postman, Documentação e Zip de Entrega

**Gauntlet:** não · **Builder:** hydrogen · **PR:** 3

**Files:**
- Modify: `NossoTeto.postman_collection.json`
- Modify: `docs/api-routes.md`
- Modify: `docs/security-ci-strategy.md`
- Create (não versionado): `tds-projetofinal-checkpoint3-caio-macedo/` e `tds-projetofinal-checkpoint3-caio-macedo.zip`

- [ ] **Step 1: Postman — variáveis**

Adicionar às `variable` da collection: `clientId=nosso-teto-web`, `clientSecret=web-dev-secret`, `mobileClientId=nosso-teto-mobile`, `mobileClientSecret=mobile-dev-secret`, `adminEmail=admin@nossoteto.local`, `adminPassword=` (vazio, preencher localmente), `adminToken=`, `mobileToken=`. Manter `auth` bearer `{{token}}` na collection.

- [ ] **Step 2: Postman — pasta "1. Autenticação (OAuth 2)"**

Substituir `1.2` e `1.4` (`/api/auth/login`) por `POST {{baseUrl}}/oauth/token`:
- `auth`: `basic` com `{{clientId}}` / `{{clientSecret}}`;
- `body`: `urlencoded` com `grant_type=password`, `username=<e-mail do usuário>`, `password=senha123`;
- script `test`:

```javascript
pm.test("200 OK", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.collectionVariables.set("token", body.access_token);   // em 1.4: "candidateToken" e "candidateId" = body.user_id
pm.collectionVariables.set("userId", body.user_id);
pm.test("refresh token só no cookie", () => pm.expect(body.refresh_token).to.be.undefined);
```

- [ ] **Step 3: Postman — nova pasta "9. Segurança (Checkpoint 3)"**, nesta ordem:
  > O cookie jar do Postman guarda o refresh **do último login** (após 1.4 é o candidato; após 9.7 é o admin). Por isso: 9.1 salva em `candidateToken`, e há um re-login do candidato antes do logout.
  1. `9.1 Refresh Token (cookie do candidato)` — `POST /oauth/token`, Basic client, `grant_type=refresh_token`; teste salva o novo `access_token` em `candidateToken` e verifica `pm.cookies.has("refresh_token")`.
  2. `9.2 Sem token → 401` — `GET /api/users/me` com `auth: noauth`; teste `status 401` e `error == "Authentication required"`.
  3. `9.3 Client mobile (scope read)` — `POST /oauth/token` com Basic `{{mobileClientId}}`/`{{mobileClientSecret}}`, `grant_type=password`; salva `mobileToken`.
  4. `9.4 Mobile tenta escrever → 403` — `POST /api/houses` com bearer `{{mobileToken}}`; teste `403` + `Insufficient permission`.
  5. `9.5 Scope inválido → 400` — `POST /oauth/token` mobile com `scope=read write`; teste `error == "invalid_scope"`.
  6. `9.6 Client secret errado → 401` — teste `error == "invalid_client"`.
  7. `9.7 Login Admin` — password grant com `{{adminEmail}}`/`{{adminPassword}}`; salva `adminToken`.
  8. `9.8 Admin lista usuários` — `GET /api/admin/users` bearer `{{adminToken}}`.
  9. `9.9 Admin remove ROLE_MANAGE_HOUSE do candidato` — `PUT /api/admin/users/{{candidateId}}/permissions` com corpo `{"permissions":["ROLE_MANAGE_PROFILE","ROLE_SEARCH_HOUSES","ROLE_MATCH","ROLE_MANAGE_EXPENSES","ROLE_MANAGE_CHORES","ROLE_MANAGE_GROCERIES","ROLE_VIEW_DASHBOARD"]}`.
  10. `9.10 Usuário comum em rota admin → 403` — `GET /api/admin/users` bearer `{{token}}`.
  11. `9.11 Re-login do candidato` — password grant com o e-mail do candidato; salva `candidateToken` (o cookie jar volta a ter o refresh do candidato).
  12. `9.12 Logout (candidato)` — `DELETE /oauth/token/revoke`, Basic client; teste `204`.
  13. `9.13 Refresh após logout → 400` — igual ao 9.1; teste `error == "invalid_grant"`.

Validar: `python -c "import json;json.load(open('NossoTeto.postman_collection.json',encoding='utf-8'))"` → sem erro. Rodar a collection inteira contra a API local (`JWT_SECRET`, `APP_CRYPTO_KEY`, `ADMIN_PASSWORD` definidos) no Postman Runner e registrar o resultado no PR.

- [ ] **Step 4: Documentação**

`docs/api-routes.md`: seção "Autenticação" reescrita (`/oauth/token` password/refresh, `/oauth/token/revoke`, cookie, códigos de erro) + seção "Administração" + matriz de permissões (copiar a tabela da spec §4).

`docs/security-ci-strategy.md`: seção 1 reescrita para o estado real (OAuth 2 Resource Server, TTLs 15 min/7 dias, refresh rotativo em cookie, permissões + scopes, Basic via profile, CORS, AES-GCM); seção 2 corrigida (`main`, Java 17, `mvn -B verify`, gate 80% de linhas, Checkstyle em modo aviso); nova seção "Como rodar localmente" com a tabela de variáveis de ambiente:

| Variável | Obrigatória | Exemplo / padrão |
|---|---|---|
| `JWT_SECRET` | sim | ≥ 32 bytes |
| `APP_CRYPTO_KEY` | sim | `openssl rand -base64 32` |
| `ADMIN_PASSWORD` | não (sem ela não há admin) | — |
| `ADMIN_EMAIL` | não | `admin@nossoteto.local` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | não | `PT15M` / `P7D` |
| `COOKIE_SECURE` | não | `false` (local) · `true` (produção) |
| `CORS_ALLOWED_ORIGINS` | não | `http://localhost:5173` |
| `WEB_CLIENT_SECRET` / `MOBILE_CLIENT_SECRET` | não | `web-dev-secret` / `mobile-dev-secret` |
| `SPRING_PROFILES_ACTIVE` | não | `basic-security` para Basic Auth |

- [ ] **Step 5: Commit**

```bash
git add NossoTeto.postman_collection.json docs/api-routes.md docs/security-ci-strategy.md
git commit -m "docs: update Postman collection and security docs for Checkpoint 3"
```

**Fim do PR 3:** revisão do Coordenador → push → `gh pr create` → CI verde → merge com autorização.

- [ ] **Step 6: Zip de entrega (após o merge do PR 3, na `main` atualizada; não versionar)**

```powershell
$dest = "tds-projetofinal-checkpoint3-caio-macedo"
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item pom.xml, NossoTeto.postman_collection.json $dest
Copy-Item -Recurse src $dest
Compress-Archive -Path $dest -DestinationPath "$dest.zip" -Force
```

Conferir: o zip contém `pom.xml`, `src/`, a collection, e **não** contém `target/` nem `uploads/`. `git status --short` mostra apenas os itens não rastreados do CP2 + os do CP3.

---

## Self-Review (Coordenador)

- **Cobertura da spec:** D1→T2 · D2→T4 · D3→T1+T5 · D4→T3 · D5→T2 (props) · D6→T6 · D7→T2 (secret) + T7 · D8→T2 · D9→T2 · D10→T5 · D11→T0 · D12→Fluxo de PRs. Tópicos 01–14 cobertos (01/02→T4, 03–06→T2, 07–09→T3, 10→T6, 11–13→T1+T5, 14→T3).
- **Invariantes:** INV-01..05, 11, 13, 14, 18 → T2 · INV-06..10 → T3 · INV-12 → T4 · INV-02, 17 → T5 · INV-16 → T6 · INV-15 → T7.
- **Consistência de nomes:** `findPermissionNamesByUserId`, `SecurityProperties.Client`, `OAuthTokenService.TokenResult`, `RefreshTokenService.Rotation`, `RefreshTokenCookieFactory.COOKIE_NAME`, `JsonAuthHandlers` usados de forma idêntica entre tarefas.
