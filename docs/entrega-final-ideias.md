# Entrega Final (07/12/2026) — Ideias e Pendências

> **Status: rascunho para discussão.** Nada aqui está decidido ou planejado. É uma lista de ideias para conversar antes de virar spec e plano.

---

## 1. Plano B / Disponibilidade

**Situação atual:** a API roda como **um único processo** ligado a **um único MySQL**. Se qualquer um dos dois cair, a API fica indisponível até alguém subir de novo. O profile `basic-security` **não** é contingência: ele vive no mesmo processo, é escolhido só no startup e rebaixaria a segurança.

O que já existe e facilita:
- O access token (JWT) é stateless, então qualquer instância valida tokens de qualquer outra.
- O estado do refresh token fica no banco, compartilhado entre instâncias.
- As migrations Flyway tornam qualquer ambiente novo reproduzível.

Ideias, da mais simples para a mais completa:
- [ ] `Dockerfile` da API + `docker-compose.yml` (API + MySQL) com `restart: always`
- [ ] Spring Boot Actuator com `/actuator/health` (liveness/readiness) para o orquestrador detectar falha
- [ ] Volume persistente e rotina de backup do MySQL
- [ ] Duas ou mais instâncias da API atrás de um load balancer (ex.: Nginx)
- [ ] Réplica do banco de dados

## 2. Lacunas conhecidas (vistas durante o Checkpoint 3)

- [ ] **Documentação OpenAPI/Swagger:** o RNF04 pede e ainda não existe (`springdoc-openapi`).
- [ ] **Testes E2E (RNF03):** Cypress ou Playwright com os golden paths de `docs/security-ci-strategy.md`, quando o frontend existir.
- [ ] **Frontend React (SPA):** consumir `/oauth/token` (form + Basic do client), refresh por cookie (`credentials: 'include'`) e CORS já configurado para `http://localhost:5173`.
- [ ] **Rate limiting / proteção contra força bruta** no `POST /oauth/token`.
- [ ] **Normalização de e-mail** (minúsculas) no cadastro e no login: o MySQL compara sem diferenciar maiúsculas, mas o código não normaliza.
- [ ] **Revogação imediata de permissões:** hoje a revogação só vale no próximo token (até 15 min). Avaliar se precisa ser imediata.
- [ ] **Criptografia:** rotação de chave (`v2:` + recifragem) e associar o texto cifrado ao `user_id` (AAD).
- [ ] **Checkstyle:** cerca de 3.300 avisos de estilo (Google) em modo não bloqueante; decidir se limpa e passa a bloquear.

## 3. Entregáveis a confirmar com o professor

- [ ] Formato da entrega final (zip, repositório, apresentação/demo)
- [ ] O que precisa estar rodando na demo (API + frontend + banco)

---

*Criado em 2026-11-24. Próximo passo: conversar sobre prioridades antes de virar spec/plano em `docs/superpowers/`.*
