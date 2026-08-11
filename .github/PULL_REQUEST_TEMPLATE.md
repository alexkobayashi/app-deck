## O que muda

<!-- Descreva a mudança em uma ou duas frases. -->

## Checklist

Servidor Go (se `server/` foi tocado):

- [ ] `gofmt -l .` não lista nada
- [ ] `go vet ./...` limpo
- [ ] `go test ./...` passa
- [ ] Nenhum arquivo do núcleo (`config`, `httpapi`, `launcher`) passou a
      importar `syscall` — código específico do Windows fica em `*_windows.go`

App Android (se `android/` foi tocado):

- [ ] `./gradlew :app:testDebugUnitTest lintDebug` passa

Sempre:

- [ ] Nenhum `config.json`, token, keystore ou senha foi commitado
- [ ] **Se alguma rota da API foi criada ou alterada:
      [`docs/api.md`](../docs/api.md) foi atualizado neste mesmo PR**

## Como testar

<!-- Passos para verificar a mudança. -->
