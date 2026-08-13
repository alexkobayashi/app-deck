# API do App Deck — contrato v2

Contrato HTTP entre o **app Android** (cliente) e o **servidor Go no Windows**.
Este documento é normativo: qualquer rota nova ou alterada precisa ser
atualizada aqui no mesmo PR que muda o código.

- **Versão do contrato:** 2.0
- **Implementado em:** `server/internal/httpapi`
- **Base URL:** `http://<ip-do-pc>:<porta>` (padrão `5050`)

## Visão geral

A comunicação é restrita à **rede local**. O servidor não deve ser exposto à
internet — sem port forward, sem túnel. Isso é uma decisão de escopo, não uma
limitação a resolver: quem tem o token pode abrir qualquer programa no PC.

Todas as requisições e respostas são JSON em UTF-8. Respostas de erro também
são JSON — nunca texto puro nem HTML.

O servidor **não sabe nada sobre ícones**. Ele conhece apenas `name` e `path`
de cada atalho; o ícone é escolhido e guardado localmente pelo app Android,
associado ao `id` do atalho.

## Autenticação

Todas as rotas **exceto `GET /api/health`** exigem o token no header:

```
Authorization: Bearer <token>
```

```bash
curl -H "Authorization: Bearer SEU_TOKEN" http://192.168.0.10:5050/api/apps
```

O token **nunca** trafega na query string. `GET /api/apps?token=...` é
recusado com 401 — o protótipo fazia isso e a v2 não aceita mais, porque a
URL fica gravada em histórico e logs de intermediários.

O token é gerado automaticamente com `crypto/rand` no primeiro start (43
caracteres, base64 URL-safe) e fica no `config.json`. A comparação é feita em
tempo constante.

Falha de autenticação responde:

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json; charset=utf-8
WWW-Authenticate: Bearer realm="app-deck"

{"error":"não autorizado: envie o header Authorization: Bearer <token>","code":"unauthorized"}
```

## Convenções

- `Content-Type` das respostas com corpo: `application/json; charset=utf-8`.
- Corpo das requisições de escrita limitado a **64 KiB**.
- Campos desconhecidos no corpo são **ignorados**, não recusados: uma versão
  nova do app pode mandar campos que um servidor antigo não conhece.
- Uma rota ou método fora do contrato responde **404 em JSON** (o servidor
  registra um catch-all para não devolver o texto puro do `net/http`). Não
  existe resposta 405.

### Status codes

| Status | Quando |
|---|---|
| 200 | Sucesso com corpo |
| 201 | Atalho criado |
| 204 | Atalho removido (sem corpo) |
| 400 | JSON inválido ou campos obrigatórios ausentes |
| 401 | Token ausente, mal formatado ou incorreto |
| 404 | Atalho ou rota inexistente |
| 413 | Corpo maior que 64 KiB |
| 500 | Falha ao abrir o programa ou ao salvar a configuração |

### Formato de erro

```json
{ "error": "mensagem legível em português", "code": "identificador_estavel" }
```

O app deve decidir o comportamento pelo `code`, não pela mensagem.

| `code` | Status | Significado |
|---|---|---|
| `unauthorized` | 401 | Token inválido → mandar o usuário rever a configuração |
| `not_found` | 404 | Atalho não existe mais → recarregar a lista |
| `invalid_json` | 400 | Bug do cliente |
| `validation_error` | 400 | Campos obrigatórios ausentes ou vazios |
| `payload_too_large` | 413 | Corpo acima do limite |
| `launch_failed` | 500 | O programa não pôde ser iniciado (ex: desinstalado) |
| `save_failed` | 500 | O servidor não conseguiu gravar o `config.json` |
| `internal_error` | 500 | Panic tratado; ver os logs do servidor |

## Modelo `App`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `id` | string | gerado pelo servidor | 16 caracteres hex. **Read-only e estável para sempre.** |
| `name` | string | sim | Nome exibido no deck. Espaços nas pontas são removidos. |
| `path` | string | sim | Caminho absoluto do executável no Windows. Normalizado — veja abaixo. |
| `args` | string[] | não | Argumentos passados ao programa. Omitido quando vazio. |

### Normalização do `path`

O servidor não devolve o `path` exatamente como foi enviado. Além de aparar
os espaços das pontas, ele **remove caracteres de formatação Unicode
invisíveis** (`U+200B`, `U+200E`, `U+200F`, `U+202A`–`U+202E`,
`U+2066`–`U+2069`, `U+FEFF`).

O motivo é prático: a caixa de *Propriedades* do Windows copia caminhos com
um `U+202A` na frente, e o usuário não tem como enxergar isso em lugar
nenhum — o atalho simplesmente falha com "executável não encontrado" num
caminho que parece correto. Há também um ganho de segurança: `U+202E`
(RIGHT-TO-LEFT OVERRIDE) permitiria que o path exibido no deck mentisse
sobre a extensão do arquivo que será executado.

`U+200C` e `U+200D` (ZWNJ e ZWJ) são preservados: têm uso linguístico
legítimo e podem fazer parte de um nome de pasta real.

**Consequência para o cliente:** o `path` na resposta de `POST` e `PUT` pode
diferir do enviado. Use sempre o valor devolvido, nunca o que você mandou.
O `name` não passa por essa limpeza.

O `id` é a chave que o app Android usa para guardar o ícone customizado.
Ele é gerado uma única vez, com `crypto/rand`, e **não muda** quando o
atalho é renomeado ou tem o caminho alterado. Editar o `config.json` à mão
e apagar um `id` faz o servidor gerar outro — e o ícone daquele atalho se
perde no app.

### Ordenação

A ordem do array em `GET /api/apps` é a ordem do `config.json`, e é a ordem
em que o deck deve exibir os atalhos por padrão. Um atalho novo entra no fim.

Reordenar é, na v1, uma preferência **local do app** (guardada no Room).
Persistir a ordem no servidor fica reservado para `PUT /api/apps/order`,
ainda não implementado.

---

## `GET /api/health`

Status do servidor. **Única rota sem autenticação.**

Usada pelo app para o indicador de conexão e para validar o pareamento antes
de salvar a configuração. O payload é deliberadamente mínimo — sem hostname,
sem caminhos, sem a lista de atalhos.

**200**
```json
{ "status": "ok", "name": "app-deck", "version": "v0.2.0" }
```

`version` é `"dev"` num binário compilado localmente.

```bash
curl http://192.168.0.10:5050/api/health
```

---

## `GET /api/apps`

Lista os atalhos configurados.

**200**
```json
{
  "apps": [
    { "id": "a3f1c09b7d24e5f6", "name": "Calculadora", "path": "C:\\Windows\\System32\\calc.exe" },
    { "id": "b7d24e5f6a3f1c09", "name": "Chrome", "path": "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", "args": ["--incognito"] }
  ]
}
```

Sem nenhum atalho configurado, `apps` é `[]` — nunca `null`.

O token **nunca** aparece nesta resposta.

```bash
curl -H "Authorization: Bearer SEU_TOKEN" http://192.168.0.10:5050/api/apps
```

---

## `POST /api/apps/{id}/launch`

Abre o programa do atalho.

Responde assim que o processo é criado, **sem esperar o programa fechar**. O
processo filho é desacoplado do servidor (`DETACHED_PROCESS`), então encerrar
o servidor não fecha os programas abertos.

**Request:** sem corpo.

**200**
```json
{ "status": "launched", "id": "a3f1c09b7d24e5f6", "name": "Chrome" }
```

**404** — id inexistente:
```json
{ "error": "atalho não encontrado: xyz", "code": "not_found" }
```

**500** — o atalho existe mas o executável não:
```json
{ "error": "não foi possível abrir Chrome: executável não encontrado: C:\\...\\chrome.exe", "code": "launch_failed" }
```

```bash
curl -X POST -H "Authorization: Bearer SEU_TOKEN" \
  http://192.168.0.10:5050/api/apps/a3f1c09b7d24e5f6/launch
```

---

## `POST /api/apps`

Cria um atalho. Ele entra no fim da lista.

**Request**

| Campo | Obrigatório | Observação |
|---|---|---|
| `name` | sim | Não pode ser vazio nem só espaços |
| `path` | sim | Não pode ser vazio nem só espaços |
| `args` | não | Lista de strings |

`id` enviado pelo cliente é ignorado — quem gera é o servidor.

```json
{ "name": "Steam", "path": "C:\\Program Files (x86)\\Steam\\steam.exe", "args": ["-silent"] }
```

**201** — o atalho criado, já com o `id`:
```json
{ "id": "c09b7d24e5f6a3f1", "name": "Steam", "path": "C:\\Program Files (x86)\\Steam\\steam.exe", "args": ["-silent"] }
```

**400** — `{"error":"name e path são obrigatórios","code":"validation_error"}`

**500** — `{"error":"não foi possível salvar a configuração no servidor","code":"save_failed"}`

O servidor **não valida** se o `path` existe na criação (o programa pode
estar num drive removível que não está conectado no momento). Um caminho
quebrado gera aviso no log no próximo start e falha em `launch` com
`launch_failed`.

```bash
curl -X POST -H "Authorization: Bearer SEU_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Steam","path":"C:\\Program Files (x86)\\Steam\\steam.exe"}' \
  http://192.168.0.10:5050/api/apps
```

---

## `PUT /api/apps/{id}`

Edita um atalho. **A atualização é parcial:** um campo ausente fica como
está. Pelo menos um entre `name`, `path` e `args` precisa vir no corpo.

O `id` nunca muda numa edição — é o que garante que o ícone customizado no
app sobreviva a uma renomeação.

```json
{ "name": "Steam (Big Picture)" }
```

Para limpar os argumentos, mande `"args": []`.

**200** — o atalho completo depois da alteração.

**400** — corpo vazio (`{}`), ou `name`/`path` enviados em branco:
```json
{ "error": "informe pelo menos um campo para alterar (name, path ou args)", "code": "validation_error" }
```

**404** — id inexistente.

```bash
curl -X PUT -H "Authorization: Bearer SEU_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Steam BP"}' \
  http://192.168.0.10:5050/api/apps/c09b7d24e5f6a3f1
```

---

## `DELETE /api/apps/{id}`

Remove um atalho.

**204** — sem corpo.

**404** — id inexistente.

Cabe ao app apagar a customização de ícone local do atalho removido.

```bash
curl -X DELETE -H "Authorization: Bearer SEU_TOKEN" \
  http://192.168.0.10:5050/api/apps/c09b7d24e5f6a3f1
```

---

## Pareamento por QR code

Implementado no servidor. **"Mostrar QR de pareamento"** no menu da bandeja
gera um PNG e o abre no visualizador de imagens padrão.

O QR contém exatamente este JSON — três campos, `port` como **número**:

```json
{ "ip": "192.168.0.10", "port": 5050, "token": "..." }
```

| Campo | Tipo | Observação |
|---|---|---|
| `ip` | string | IPv4 da LAN escolhido pelo servidor (ver abaixo) |
| `port` | número | Porta em que o servidor está escutando de fato, já considerando `--port` |
| `token` | string | Token em vigor, 43 caracteres quando gerado pelo servidor |

Quando o PC tem mais de um IPv4, o servidor escolhe o mais provável de ser
alcançável pelo celular: interfaces virtuais (WSL, Docker, Hyper-V, VMware,
VirtualBox) são descartadas e as faixas domésticas vêm primeiro
(`192.168.x` → `10.x` → `172.16-31.x`). Todos os endereços detectados são
registrados no log do servidor.

Se o PC não tiver nenhum IPv4 utilizável, nenhum QR é gerado — mostrar um QR
que o app não conseguiria usar seria pior que a mensagem de erro no log.

### O que o app faz

Implementado no app Android (`ui/serverconfig` + `data/scanner`):

1. Escaneia e interpreta o JSON, **ignorando campos desconhecidos** (para
   tolerar acréscimos futuros).
2. Aceita `port` como número **ou** string. O servidor sempre manda número,
   mas o `config.json` do protótipo gravava a porta como string e ser
   tolerante aqui é barato.
3. Valida: `ip` sem esquema nem barra, `port` entre 1 e 65535, `token` não
   vazio.
4. **Chama `GET /api/health` antes de salvar.** A configuração só é
   persistida se responder 200 — assim um QR de outro app, ou um QR de um
   servidor que já mudou de IP, falha na hora do pareamento em vez de virar
   um deck quebrado. Se a validação falhar, os campos ficam preenchidos com o
   que foi lido, para o usuário corrigir à mão em vez de recomeçar.

O leitor usa o Google Play Services (`play-services-code-scanner`), que
**não exige permissão de câmera** — a captura roda no processo do Play
Services. Em aparelhos sem Play Services a leitura falha com mensagem
própria e a configuração manual continua disponível.

### Ciclo de vida do arquivo

O PNG é gravado em `%LOCALAPPDATA%\AppDeck\pairing.png` com permissão
restrita e **apagado quando o servidor é encerrado pelo menu "Sair"** — o
arquivo carrega o token em claro e não deve ficar no disco. Encerrar o
processo à força (Gerenciador de Tarefas) deixa o arquivo para trás; ele é
sobrescrito no próximo "Mostrar QR".

---

## Erros comuns em campo

| Sintoma | Causa provável |
|---|---|
| O app não acha o servidor | Firewall do Windows bloqueando a porta em "Redes privadas", ou celular em outra rede/Wi-Fi de visitante |
| Funcionava e parou | O IP do PC mudou (DHCP) — reparear pelo QR ou corrigir o IP à mão |
| 401 em tudo | Token digitado errado, ou o `config.json` foi recriado (token novo) |
| `launch_failed` | O programa foi desinstalado, atualizado para outro caminho, ou está num drive desconectado |
| O deck aparece vazio | Nenhum atalho configurado ainda |

## Changelog do contrato

### 2.0 — inicial

Primeira versão da API JSON. Diferenças em relação ao protótipo
(`reference/main.go`, que servia HTML e tinha só `GET /` e `/abrir`):

- Token no header `Authorization: Bearer`, nunca na query string.
- Comparação de token em tempo constante.
- Atalhos identificados por `id` estável em vez de pelo `name`.
- Campo `icon` removido do servidor — o ícone é responsabilidade do app.
- `porta` (string) virou `port` (inteiro).
- CRUD completo de atalhos em runtime, com persistência atômica.
- Todas as respostas, inclusive erros, em JSON estruturado.
