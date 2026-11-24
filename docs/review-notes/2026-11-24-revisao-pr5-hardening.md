# Revisão guiada do PR #5 — Hardening (T6–T8)

> Notas da conversa de revisão de 2026-11-24 sobre o PR #5 (`feat/checkpoint-3-hardening` → `feat/checkpoint-3-auth-core`, empilhado sobre o #4).
> Continuação de [`2026-11-24-revisao-pr4-auth-core.md`](2026-11-24-revisao-pr4-auth-core.md).
> Referências: spec `docs/superpowers/specs/2026-11-23-checkpoint-3-security-design.md` (D7, §3.2, INV-14, INV-15).

---

## Criptografia de dados sensíveis (T7)

**Por quê:** o RNF09 pede que dados pessoais críticos sejam encriptados no banco. `allergies` (dado de saúde) e `bio` são gravados cifrados; quem rouba uma cópia do banco não lê esses campos.

### As peças do pacote `security/crypto`

| Classe | Papel |
|---|---|
| **`AesGcmFieldCipher`** | O **motor**: **cifra** (`encrypt`) e **decifra** (`decrypt`) |
| `FieldCipherConfig` | No startup, lê `APP_CRYPTO_KEY`, valida (base64 e exatamente 32 bytes) e cria o motor. Sem chave válida, a API não sobe |
| `FieldCipherHolder` | Guarda o motor num lugar estático, para o Hibernate encontrá-lo. Existe porque o teste de schema monta o Hibernate **sem Spring**. Se for usado antes de inicializado, falha explicitamente |
| `EncryptedStringConverter` | Ponte com o JPA: chama `encrypt` ao gravar e `decrypt` ao ler |
| `UserProfile` | `@Convert(converter = EncryptedStringConverter.class)` em `allergies` e `bio` |

Para o resto do código a criptografia é **invisível**: o `UserService` lê e grava `profile.getBio()` normalmente, e o converter cifra e decifra no caminho entre a entidade e o banco.

```
Gravar:  "Alergia a amendoim"     ──encrypt()──►  "v1:q8f3...Zx=="   → banco
Ler:     banco "v1:q8f3...Zx=="   ──decrypt()──►  "Alergia a amendoim"
```

### `AesGcmFieldCipher`: cifra **e** decifra

**Construtor:** exige uma chave de **exatamente 32 bytes** (AES-256).

**`encrypt`, no código:**
```java
byte[] iv = new byte[12];
RANDOM.nextBytes(iv);                                    // ① IV aleatório, novo a cada cifragem
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); // ② algoritmo
cipher.init(ENCRYPT_MODE, key, new GCMParameterSpec(128, iv)); // ③ chave + IV + tag de 128 bits
byte[] ciphertext = cipher.doFinal(textoEmBytes);        // ④ cifra e anexa a tag
return "v1:" + Base64( iv ‖ ciphertext );                // ⑤⑥ junta e vira texto
```
A matemática fica na biblioteca padrão do Java (`javax.crypto`). A classe só escolhe **o algoritmo e os parâmetros certos**: criptografia não se escreve "na mão".

**`decrypt`:**
1. Tira o `v1:` e decodifica o base64. Inválido gera erro.
2. Confere o tamanho mínimo (IV + tag). Truncado gera erro.
3. Decifra e **confere a tag**. Um bit alterado ou uma chave errada gera exceção; ele **nunca devolve lixo**.

Mensagens de erro genéricas, sem mostrar texto cifrado nem texto claro.

**Formato gravado:**
```
"v1:" + base64( IV (12 bytes) ‖ texto cifrado ‖ tag (16 bytes) )
```
O `v1:` marca "cifrado, formato versão 1": separa de dados antigos em texto puro e abre caminho para uma futura troca de chave (`v2:`).

### Como o AES-GCM funciona por dentro

**AES** é uma **cifra de bloco**: embaralha blocos de 16 bytes com a chave (14 rodadas no AES-256). Sem a chave, é inviável desfazer.

**GCM** é o **modo** de uso, e faz duas coisas:

**a) Sigilo (modo contador).** O AES cifra uma sequência de contadores derivada do IV, gerando um fluxo pseudoaleatório. O texto é combinado com esse fluxo por **XOR**:
```
contador:  IV|1     IV|2     IV|3   ...
              ↓ AES    ↓ AES    ↓ AES      (chave secreta)
fluxo:     k1k1k1   k2k2k2   k3k3k3 ...
              ⊕        ⊕        ⊕
texto:     "Alergia a amendoim"
              =
cifrado:   8f 3a c1 ...                    (mesmo tamanho do texto)
```
Para decifrar, gera-se o mesmo fluxo (mesma chave e IV) e faz-se XOR de novo. Por isso o **IV nunca pode se repetir** com a mesma chave: o fluxo se repetiria e dois textos cifrados poderiam ser cruzados. O IV aleatório também faz o mesmo texto cifrado duas vezes dar resultados diferentes, então ninguém descobre que dois usuários têm a mesma alergia.

**b) Integridade (a tag).** Em paralelo, o GCM calcula uma **tag de 16 bytes**, um resumo do texto cifrado dependente da chave. Na leitura ela é recalculada: igual → íntegro; diferente → adulterado ou chave errada, e a leitura é recusada.

> **Sigilo** = ninguém lê. **Integridade** = ninguém altera sem ser percebido. Sem a tag, daria para adulterar o dado às cegas.

### Tamanho e a migration V4

Exemplo: `"Alergia a amendoim"` (18 bytes):
```
IV (12) + cifrado (18) + tag (16) = 46 bytes → base64 ≈ 64 caracteres → + "v1:" = 67 caracteres
```
O valor guardado é cerca de **4/3 do original mais 40 bytes**. Por isso a **V4** mudou `allergies` (era `VARCHAR(255)`) e `bio` (era `TEXT`, máximo de 64 KB no MySQL) para **`MEDIUMTEXT`**. Esse limite foi um achado do Gauntlet (rodada 2 do T7): os testes no H2 não acusavam, porque lá a coluna vira CLOB sem limite. A correção foi validada num **MySQL 8 real** com uma `bio` de 60 KB.

### Dados antigos (texto puro)

Perfis gravados antes do CP3 estão em texto puro. O converter trata assim:
- valor **com** `v1:` → decifra;
- valor **sem** `v1:` → é legado e devolve como está; no **próximo save**, é gravado cifrado.

Assim não foi preciso uma migração de dados. Um teste prova a recifragem: grava texto puro direto no banco, atualiza o perfil pela API e confere que a coluna passou a começar com `v1:`.

### Cuidados com a chave

- `APP_CRYPTO_KEY` é **obrigatória** e **não pode mudar** depois do primeiro uso. Com outra chave, a tag não confere e os perfis cifrados ficam ilegíveis.
- Guardar uma cópia segura, **fora do Git** (o `.env` é ignorado).
- Rotação de chave (`v2:`) e associar o texto cifrado ao `user_id` (AAD) ficaram como ideia em `docs/entrega-final-ideias.md`.

---

## Ainda não revisado nesta conversa

- [ ] **CORS (T6):** `CommonSecurityConfig.corsConfigurationSource`, origens explícitas com credenciais
- [ ] **Postman (T8):** pasta 9 (segurança) e o cuidado com o cookie jar
- [ ] **Documentação (T8):** `api-routes.md` e `security-ci-strategy.md`
- [ ] **`.env` / README** (já comentados na parte 1)
