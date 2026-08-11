# App Deck

Transforme seu celular Android num **deck de atalhos** para o PC: cada botão,
ao ser tocado, abre um programa específico num computador Windows na mesma
rede Wi-Fi.

```
[App Android] <--HTTP + JSON--> [Servidor Go no Windows] --exec.Command--> [Programa abre]
```

## Status

Projeto em fase inicial. Hoje o repositório contém apenas o **protótipo de
referência** funcional em [reference/](reference/) — um servidor Go mínimo que
serve uma página web com botões. O servidor v2 e o app Android nativo estão em
desenvolvimento.

| Componente | Estado |
|---|---|
| Protótipo de referência (`reference/`) | Funcional |
| Servidor Go v2 (`server/`) | Planejado |
| App Android (`android/`) | Planejado |
| Documentação da API (`docs/api.md`) | Planejado |

Veja o roadmap completo em [CLAUDE.md](CLAUDE.md).

## Componentes

**Servidor Windows (Go)** — API HTTP em JSON, autenticação por token no header
`Authorization`, ícone na bandeja do sistema, inicialização automática com o
Windows e pareamento por QR code.

**App Android nativo** — Kotlin + Jetpack Compose. Grade de atalhos com ícone
totalmente customizável (galeria do dispositivo, pacote de ícones embutido ou
emoji), reordenação por arrasto e gerenciamento dos atalhos direto pelo app.

## Segurança e escopo de rede

A comunicação é **restrita à rede local**. O projeto não expõe o servidor à
internet — sem port forward, sem túnel. Isso é uma decisão de design, não uma
limitação a ser resolvida.

O token de autenticação trafega apenas no header `Authorization: Bearer <token>`,
nunca na URL. Se nenhum token estiver configurado no primeiro start, o servidor
gera um aleatório com `crypto/rand`.

> **Nunca commite um `config.json` com token real.** O `.gitignore` já bloqueia
> esse arquivo — use `config.example.json` como modelo versionado.

## Executando o protótipo

Instruções em [reference/PROTOTIPO_README.md](reference/PROTOTIPO_README.md).

Resumo:

```bash
cd reference
GOOS=windows GOARCH=amd64 go build -o app-deck.exe main.go
```

Copie `config.json`, ajuste os caminhos dos programas e o token, e rode o `.exe`.

## Estrutura do repositório

```
app-deck/
├── server/              # servidor Go (em desenvolvimento)
├── android/             # app Android (em desenvolvimento)
├── docs/api.md          # contrato da API entre os dois projetos
├── reference/           # protótipo original — consulta apenas, não editar
└── .github/workflows/   # CI: build + testes
```

Monorepo: os dois projetos evoluem junto com a API que é o contrato entre eles.

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
