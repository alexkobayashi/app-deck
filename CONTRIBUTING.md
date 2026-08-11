# Contribuindo com o App Deck

Obrigado pelo interesse! Este documento cobre o essencial para abrir um PR.

## Licenciamento das contribuições

Ao enviar um pull request, você concorda que sua contribuição será licenciada
sob a [Apache License 2.0](LICENSE), conforme a Seção 5 da própria licença.
Não é necessário assinar um CLA separado.

## Antes de abrir um PR

**Código Go** — todo código deve passar limpo por:

```bash
gofmt -l .        # não deve listar nenhum arquivo
go vet ./...
go test ./...
```

**Documentação da API** — se você adicionou ou alterou uma rota HTTP, atualize
`docs/api.md` no mesmo PR. Esse arquivo é o contrato entre o servidor e o app
Android e não pode ficar desatualizado.

**Segredos** — nunca inclua um `config.json` com token real. Use
`config.example.json`.

## Escopo do projeto

Antes de investir tempo numa feature grande, abra uma issue para discutir. Os
seguintes itens estão **fora de escopo** na v1:

- Acesso pela internet fora da rede local (VPN, port forward, túnel)
- Múltiplos servidores/PCs conectados no mesmo app
- Ações além de "abrir programa" (desligar PC, mouse/teclado remoto)

## Reportando bugs

Abra uma issue incluindo:

- Versão do servidor (`GET /api/health`) e do app Android
- Versão do Windows e do Android
- Passos para reproduzir e o comportamento esperado
- Trechos relevantes do log (removendo o token, se aparecer)

## Reportando vulnerabilidades

Não abra issue pública para falhas de segurança. Use a aba **Security >
Report a vulnerability** do repositório no GitHub.
