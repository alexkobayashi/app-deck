# App Deck

Transforme seu celular Android num **deck de atalhos** para o PC: cada botão,
ao ser tocado, abre um programa específico num computador Windows na mesma
rede Wi-Fi.

```
[App Android] <--HTTP + JSON--> [Servidor Go no Windows] --exec.Command--> [Programa abre]
```

## Status

[![CI](https://github.com/alexkobayashi/app-deck/actions/workflows/ci.yml/badge.svg)](https://github.com/alexkobayashi/app-deck/actions/workflows/ci.yml)

| Componente | Estado |
|---|---|
| Servidor Go (`server/`) | **Funcional** |
| App Android (`android/`) | **Funcional** |
| Documentação da API (`docs/api.md`) | **Completa** (contrato v2) |
| Protótipo de referência (`reference/`) | Funcional (histórico) |

**Servidor:** API JSON (`/api/health`, CRUD de atalhos e `launch`) com
autenticação por token no header, persistência atômica do `config.json`,
logging estruturado, ícone na bandeja do sistema, inicialização automática com
o Windows e QR code de pareamento. Requer Go 1.25+ para compilar; quem só usa
baixa o `.exe` do Release. Ver [server/README.md](server/README.md).

**App Android:** Kotlin + Compose. Grade de atalhos que abre os programas no
PC, ícone customizável por atalho (emoji ou foto da galeria), reordenação por
arrasto, criar/editar/excluir atalhos pelo app, pareamento por QR code,
indicador de conexão e cache local para o deck abrir instantâneo e continuar
visível offline. Ver [android/README.md](android/README.md).

Fora do escopo por ora: pacote de ícones vetoriais embutidos (das três origens
de ícone previstas, emoji e galeria estão feitas), múltiplos servidores no
mesmo app, e persistir a ordem dos atalhos no servidor — hoje ela é preferência
local do aparelho.

Veja o roadmap completo em [CLAUDE.md](CLAUDE.md).

## Componentes

**Servidor Windows (Go)** — API HTTP em JSON, autenticação por token no header
`Authorization`, ícone na bandeja do sistema, inicialização automática com o
Windows e pareamento por QR code.

**App Android nativo** — Kotlin + Jetpack Compose. Grade de atalhos com ícone
customizável por atalho (emoji ou foto da galeria), reordenação por arrasto e
gerenciamento dos atalhos direto pelo app.

O ícone é escolhido e guardado **no aparelho**, amarrado ao `id` estável de
cada atalho — o servidor nunca sabe nada sobre ícones. Renomear o atalho ou
trocar o executável no PC não perde a escolha.

## Segurança e escopo de rede

A comunicação é **restrita à rede local**. O projeto não expõe o servidor à
internet — sem port forward, sem túnel. Isso é uma decisão de design, não uma
limitação a ser resolvida.

O token de autenticação trafega apenas no header `Authorization: Bearer <token>`,
nunca na URL. Se nenhum token estiver configurado no primeiro start, o servidor
gera um aleatório com `crypto/rand`.

> **Nunca commite um `config.json` com token real.** O `.gitignore` já bloqueia
> esse arquivo — use `config.example.json` como modelo versionado.

## Rodando o servidor

```bash
cd server
go build -o app-deck-server.exe ./cmd/deck-server
./app-deck-server.exe
```

No primeiro start o servidor cria um `config.json` com um token aleatório e
mostra no log o endereço para configurar no app. Libere a porta no Firewall
do Windows para "Redes privadas". Detalhes, flags e configuração em
[server/README.md](server/README.md); o contrato da API em
[docs/api.md](docs/api.md).

Um `config.json` no formato do protótipo é migrado automaticamente (com
backup) no primeiro start.

## Rodando o app Android

```bash
cd android
./gradlew :app:installDebug
```

No app, toque na engrenagem e em **Escanear QR do servidor** — na bandeja do
Windows, o menu do App Deck tem "Mostrar QR de pareamento". Isso evita digitar
o token de 43 caracteres no teclado do celular. Configurar à mão (IP, porta,
token) continua disponível. Detalhes em [android/README.md](android/README.md).

Um atalho é um par `path` + `args`. Além de programas, dá para abrir sites,
apps instalados pelo Chrome e o terminal como administrador — as receitas
prontas, com as limitações de cada uma, estão em
[docs/atalhos.md](docs/atalhos.md).

## Estrutura do repositório

```
app-deck/
├── server/              # servidor Go — API JSON, bandeja, autostart, QR
├── android/             # app Android — Kotlin + Compose
├── docs/api.md          # contrato da API entre os dois projetos
├── docs/atalhos.md      # receitas de atalhos (sites, PWAs, terminal admin)
├── docs/release.md      # como publicar uma versão (keystore, secrets, tag)
├── reference/           # protótipo original — consulta apenas, não editar
└── .github/workflows/   # CI: build + testes; release por tag
```

O protótipo original continua disponível em
[reference/PROTOTIPO_README.md](reference/PROTOTIPO_README.md) como
referência histórica.

Monorepo: os dois projetos evoluem junto com a API que é o contrato entre eles.

## Publicando

Uma tag `v*` compila e publica os dois artefatos no mesmo GitHub Release —
`.exe` do servidor e `.apk` do app. Passo a passo em
[docs/release.md](docs/release.md).

## Contribuindo

Contribuições são bem-vindas. Veja [CONTRIBUTING.md](CONTRIBUTING.md).

## Licença

Distribuído sob a [Licença Apache 2.0](LICENSE).

```
Copyright 2026 Alex Kobayashi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
