# App Deck — spec do projeto

Controle remoto que transforma o celular Android num "deck" de atalhos:
cada botão, ao ser tocado, abre um programa específico num PC Windows na
mesma rede Wi-Fi. Este arquivo é o brief para evoluir um protótipo já
funcional em dois produtos robustos, publicados num repositório no GitHub.

## Componentes

1. **Servidor Windows (Go)** — mais robusto que o protótipo: API HTTP em
   JSON, autenticação melhor, ícone na bandeja do sistema, inicialização
   automática com o Windows.
2. **App Android nativo** — substitui a página web do protótipo. Permite
   customizar o ícone de cada atalho, configurar o servidor e reorganizar
   os atalhos.

## Protótipo de referência

A pasta `reference/` deste repositório contém um MVP já funcional e testado
(compila limpo com `GOOS=windows GOARCH=amd64 go build`):

- `reference/main.go` — servidor Go mínimo: lê `config.json`, serve uma
  página HTML com botões, executa o `.exe` correspondente ao tocar.
- `reference/config.json` — exemplo de configuração (token, porta, lista de
  apps com nome/emoji/caminho).
- `reference/PROTOTIPO_README.md` — como compilar/rodar esse protótipo.

Use como referência de comportamento esperado (a lógica de abrir o programa
via `exec.Command(path)`, a leitura de config, etc. já foi validada), não
como base obrigatória de código — pode reescrever do zero seguindo os
requisitos abaixo. Limitações conhecidas do protótipo que a v2 deve resolver:
token na query string (inseguro), roda como janela de console, `config.json`
só é lido no startup (sem API de gerenciamento), sem testes.

## Arquitetura alvo

```
[App Android] <--HTTP + JSON--> [Servidor Go no Windows] --exec.Command--> [Programa abre]
```

- Comunicação restrita à rede local (LAN). Não expor à internet (sem port
  forward, sem túnel) — isso é intencional, não uma limitação a resolver.
- Autenticação por token, enviado no header `Authorization: Bearer <token>`
  (nunca mais na query string).
- O servidor expõe uma API JSON; o app Android consome essa API e cuida de
  toda a parte visual (ícones, layout, customização) localmente — o
  servidor não sabe nada sobre ícones.

## Requisitos — Servidor (Go)

### API HTTP (JSON)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/health` | Status e versão do servidor |
| GET | `/api/apps` | Lista os atalhos configurados (id, name, path) |
| POST | `/api/apps/{id}/launch` | Executa o programa correspondente |
| POST | `/api/apps` | Adiciona um novo atalho |
| PUT | `/api/apps/{id}` | Edita um atalho existente |
| DELETE | `/api/apps/{id}` | Remove um atalho |

Todas as rotas exceto `/api/health` exigem `Authorization: Bearer <token>`.
Responder sempre em JSON estruturado, incluindo erros: `{"error": "..."}`.
Documentar o contrato completo (request/response de cada rota) em
`docs/api.md` conforme for implementado.

### Robustez
- Persistência real do `config.json`: gravar em arquivo temporário e mover
  por cima do original (evita corromper o arquivo se o processo morrer no
  meio da escrita), já que agora a API permite editar atalhos em runtime.
- Validar o `path` de cada atalho no startup (o arquivo existe?) e logar
  avisos claros em vez de falhar silenciosamente ou travar.
- Testes unitários para os handlers HTTP (`net/http/httptest`) e para a
  lógica de carregar/salvar config.
- Logging estruturado com `log/slog` (já vem na stdlib do Go) em vez de
  `fmt.Println`.
- Rodar como aplicativo de bandeja do sistema, não como janela de console
  (ex: lib `getlantern/systray` ou equivalente). Menu da bandeja com pelo
  menos: "Mostrar QR de pareamento", "Abrir pasta de logs", "Sair".
- Opção de iniciar junto com o Windows, gravando em
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
  (`golang.org/x/sys/windows/registry`), ativável/desativável pelo menu da
  bandeja — sem o usuário precisar mexer manualmente na pasta de
  inicialização.

### Segurança
- Token só trafega no header `Authorization`, nunca na URL.
- Se nenhum token estiver configurado no primeiro start, gerar um
  aleatoriamente e seguro (`crypto/rand`) em vez de depender do usuário
  digitar uma senha fraca.
- Gerar um **QR code** (mostrado no console/bandeja) contendo
  `{"ip": "...", "port": ..., "token": "..."}` para o app Android escanear e
  se parear automaticamente, sem digitação manual.
- HTTPS com certificado autoassinado é upgrade futuro (fase avançada), não
  bloqueante para a v1 — o tráfego já fica restrito à rede local.

### Empacotamento / distribuição
- GitHub Actions: workflow que compila o `.exe` a cada release (tag) e
  anexa como artefato no GitHub Release, para quem for usar não precisar
  ter Go instalado.
- Ícone do executável e metadados de versão embutidos (ex: `go-winres`).

## Requisitos — App Android

### Stack sugerida
- Kotlin + Jetpack Compose.
- Persistência local com Room: guarda o servidor configurado (IP, porta,
  token) e as customizações de cada atalho (ícone escolhido, ordem).
- Cliente HTTP com Retrofit/OkHttp ou Ktor.

### Funcionalidades principais
- Tela principal: grade de botões (o deck), um por atalho, com ícone
  customizado e nome.
- Tocar chama `POST /api/apps/{id}/launch` e mostra feedback (sucesso, erro,
  sem conexão).
- Tela de configuração do servidor (IP, porta, token), com opção de
  **escanear QR code** (CameraX + ML Kit ou ZXing) para preencher tudo de
  uma vez.
- Reordenar atalhos por arrastar.
- Adicionar/editar/remover atalhos direto do app, via API de gerenciamento
  do servidor.
- Indicador de status de conexão com o servidor.

### Customização de ícones — requisito principal do usuário
Cada atalho deve ter um ícone independente do que o servidor manda (o
servidor só manda nome + caminho, nunca imagem). Origens possíveis do ícone,
por atalho:
1. **Galeria do dispositivo** — Photo Picker do Android
   (`ActivityResultContracts.PickVisualMedia`), com recorte/redimensionamento,
   salvando uma cópia local no armazenamento interno do app, associada ao
   ID do atalho.
2. **Pacote de ícones embutido** — conjunto de ícones vetoriais comuns
   (navegador, música, jogo, documento, planilha, editor de código, etc.)
   para quem não quer usar imagem própria.
3. **Emoji** — fallback simples, mantém compatibilidade com o protótipo.

Persistir a escolha localmente (Room), vinculada ao ID do atalho vindo da
API. Reinstalar o app perde as customizações (aceitável na v1); trocar a
config no servidor não deve bagunçar os ícones já escolhidos.

**Fase avançada (não v1, registrar como issue futura):** endpoint no
servidor que extrai o ícone real do `.exe` do Windows (`ExtractIconEx`, via
`syscall`) e serve como PNG em `GET /api/apps/{id}/icon`, usado só como
sugestão inicial — o usuário ainda pode sobrescrever localmente.

## Estrutura sugerida do repositório

```
app-deck/
├── server/              # projeto Go
│   ├── cmd/deck-server/
│   ├── internal/
│   ├── go.mod
│   └── README.md
├── android/              # projeto Android (Gradle)
│   └── ...
├── docs/
│   └── api.md            # contrato da API, documentado à medida que evolui
├── reference/            # protótipo original (não editar, só consultar)
├── .github/workflows/     # CI: build + testes do servidor, build do APK
├── LICENSE
└── README.md              # visão geral do monorepo
```

Monorepo — os dois projetos evoluem junto com a API que é o contrato entre
eles, faz sentido versionar tudo junto.

`.gitignore`: nunca commitar um `config.json` com token real — usar
`config.example.json` versionado e `config.json` ignorado.

## Roadmap sugerido

1. **Servidor v2** — API JSON, auth por header, persistência com escrita
   segura, testes, logging estruturado.
2. **Repo no GitHub** — estrutura de pastas, `.gitignore`, CI básico
   (build + testes do servidor a cada push).
3. **Servidor v3** — bandeja do sistema, autostart, QR de pareamento.
4. **App Android v1** — tela do deck consumindo a API, launch de apps,
   configuração manual de servidor (sem QR ainda).
5. **App Android v2** — customização de ícones (as 3 origens acima),
   reordenar, adicionar/editar/remover atalhos.
6. **App Android v3** — pareamento via QR code.
7. **Release** — CI gerando `.exe` versionado e, se fizer sentido, o
   `.apk`/`.aab` do app.

## Fora de escopo (v1)

- Acesso pela internet fora da rede local — sem VPN, sem port forward.
- Múltiplos servidores/PCs no mesmo app (por enquanto, um app conecta em um
  servidor por vez).
- Ações além de "abrir programa" (desligar PC, mouse/teclado remoto, etc.) —
  pode ser uma v2 do produto, não é objetivo agora.

## Notas de execução

- Priorize ter algo rodando ponta a ponta cedo (servidor v2 básico + app
  Android v1 simples) antes de polir a customização de ícones — permite
  testar no celular de verdade desde a primeira fase.
- Todo código Go gerado deve passar por `gofmt` e `go vet` antes de ser
  considerado pronto.
- Atualizar `docs/api.md` a cada rota nova ou alterada — é o contrato entre
  os dois projetos, não pode ficar desatualizado.
