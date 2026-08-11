# App Deck — servidor Windows

Servidor Go que expõe uma API JSON na rede local e abre programas no Windows
quando o app Android pede.

O contrato completo da API está em [`docs/api.md`](../docs/api.md).

## Compilar

Requer Go 1.23 ou mais novo.

```bash
cd server
go build -o app-deck-server.exe ./cmd/deck-server
```

Cross-compilando de Linux ou macOS:

```bash
GOOS=windows GOARCH=amd64 go build -o app-deck-server.exe ./cmd/deck-server
```

## Rodar

```bash
./app-deck-server.exe
```

No primeiro start, se não existir `config.json`, o servidor cria um com um
token aleatório de 43 caracteres e nenhum atalho. O caminho do arquivo e o
endereço de escuta aparecem no log.

Libere a porta no **Firewall do Windows** para "Redes privadas" na primeira
execução, senão o celular não alcança o servidor.

### Flags

| Flag | Padrão | Descrição |
|---|---|---|
| `--config` | ver abaixo | Caminho do `config.json` |
| `--port` | do config | Porta de escuta (sobrepõe o config) |
| `--bind` | do config | Endereço de escuta; `127.0.0.1` restringe ao próprio PC |
| `--log-level` | `info` | `debug`, `info`, `warn` ou `error` |
| `--log-dir` | `%LOCALAPPDATA%\AppDeck\logs` | Diretório dos logs |
| `--version` | — | Mostra a versão e sai |

## Onde ficam os arquivos

O `config.json` é procurado nesta ordem:

1. o caminho passado em `--config`;
2. a variável de ambiente `APPDECK_CONFIG`;
3. `config.json` ao lado do `.exe` — **modo portátil**, usado se o arquivo já
   existir ou se o diretório for gravável;
4. `%APPDATA%\AppDeck\config.json`.

A checagem de gravabilidade existe porque um `.exe` dentro de
`C:\Program Files` não consegue gravar no próprio diretório, e toda edição de
atalho feita pelo app falharia.

Logs: `%LOCALAPPDATA%\AppDeck\logs\app-deck-server.log` (JSON, uma linha por
evento, rotacionado a cada 5 MB mantendo uma geração `.1`).

## Configuração

Veja [`config.example.json`](config.example.json). Nunca commite um
`config.json` com token real — ele está no `.gitignore`.

```json
{
  "version": 2,
  "token": "",
  "port": 5050,
  "bind": "0.0.0.0",
  "apps": [
    { "id": "a3f1c09b7d24e5f6", "name": "Calculadora", "path": "C:\\Windows\\System32\\calc.exe" }
  ]
}
```

- `token` vazio faz o servidor gerar um forte no próximo start. Um token
  digitado à mão com menos de 32 caracteres gera aviso no log.
- `bind` `0.0.0.0` aceita conexões da LAN; `127.0.0.1` restringe ao próprio PC.
- `id` é gerado pelo servidor e **não deve ser editado à mão**: é a chave que
  o app Android usa para guardar o ícone customizado de cada atalho.
- `apps[].args` é opcional.

### Migração do protótipo

Um `config.json` no formato do protótipo (`reference/config.json`: `porta`
como string, `icon` junto, sem `id`) é migrado automaticamente no primeiro
start. O arquivo original é preservado como `config.json.v1.bak`. Os emojis
em `icon` são descartados — na v2 o ícone é escolhido no app.

Editar o `config.json` com o servidor rodando não tem efeito: o arquivo é
lido no start e reescrito a cada alteração feita pela API. Use o app (ou
reinicie o servidor).

## Testes

```bash
cd server
gofmt -l .          # não deve listar nada
go vet ./...
go test ./...
go test -race ./...  # exige CGO e um compilador C
```

O detector de corrida precisa de `CGO_ENABLED=1` e gcc. No Windows sem
toolchain C ele não roda; o job Linux do CI cobre esse caso.

Nenhum pacote do núcleo (`config`, `httpapi`, `launcher`) importa
`syscall`, então toda a lógica é testável em Linux. O código específico do
Windows fica em arquivos `*_windows.go` com um stub `*_other.go`.

## Segurança

- O token só é aceito no header `Authorization: Bearer`. Na query string é
  recusado com 401.
- O token nunca é escrito no log, nem no `/api/health`, nem em
  `GET /api/apps`.
- **Quem tem o token abre qualquer programa neste PC.** A API de
  gerenciamento aceita qualquer caminho de executável — isso é intencional
  para um cliente autenticado. Não exponha o servidor à internet.
- HTTPS com certificado autoassinado é upgrade futuro; o tráfego já fica
  restrito à rede local.

## Estrutura

```
server/
├── cmd/deck-server/       # main, flags, ciclo de vida
└── internal/
    ├── config/            # modelo, migração v1→v2, escrita atômica, Store
    ├── httpapi/           # rotas, auth Bearer, middlewares, respostas JSON
    ├── launcher/          # execução dos programas (interface + Fake p/ testes)
    ├── logging/           # slog em console + arquivo JSON com rotação
    ├── netinfo/           # descoberta dos IPs da LAN
    ├── token/             # geração e comparação em tempo constante
    └── version/           # metadados injetados via -ldflags
```
