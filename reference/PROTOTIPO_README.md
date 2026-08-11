# APP-Deck — servidor Go + página web

Servidor leve que roda no Windows, mostra uma página de "deck" com botões e,
ao tocar em um botão pelo celular, abre o programa correspondente no PC.

Arquivos:
- `main.go` — o servidor
- `config.json` — sua senha, porta e a lista de apps (edite este arquivo à vontade)

## 1. Configurar o `config.json`

Abra `config.json` num editor de texto e ajuste:

- **`token`**: troque por uma senha sua (qualquer texto, sem espaços). É o que impede
  qualquer pessoa na sua rede de abrir programas no seu PC sem permissão.
- **`porta`**: pode deixar `5050`, a não ser que já use essa porta para outra coisa.
- **`apps`**: cada item tem `name` (nome do botão), `icon` (um emoji) e `path`
  (caminho completo do `.exe`).

**Como achar o caminho de um programa:** clique com o botão direito no atalho do
programa (na área de trabalho ou no menu Iniciar) → **Propriedades** → copie o
campo **Destino**. No JSON, toda barra invertida `\` precisa ser duplicada (`\\`),
por isso os caminhos no exemplo aparecem com `\\`.

## 2. Compilar

Com o Go já instalado (veja o passo a passo que combinamos antes), abra o
**Prompt de Comando** ou **PowerShell** dentro da pasta com os dois arquivos e rode:

```
go build -o dock-server.exe main.go
```

Isso gera o `dock-server.exe`. Esse `.exe` já é o programa final — a partir de
agora você só precisa dele e do `config.json` na mesma pasta; não precisa mais
do Go instalado para rodá-lo (só se quiser editar e recompilar depois).

## 3. Rodar

Dê duplo clique em `dock-server.exe` (ou rode `.\dock-server.exe` no terminal).

Na primeira vez, o **Firewall do Windows** vai perguntar se permite o acesso —
marque **Redes privadas** e clique em **Permitir acesso**.

O terminal vai mostrar algo como:

```
Servidor rodando!
No celular (mesma rede Wi-Fi), acesse:
http://192.168.0.42:5050/?token=troque-esta-senha-123
```

## 4. Acessar do celular

Com o celular **na mesma rede Wi-Fi** do PC, abra o navegador e digite o
endereço que apareceu no terminal (com o `?token=...` no final).

Para deixar com cara de app: no Chrome do Android, toque no menu (⋮) →
**Adicionar à tela inicial**. Vai aparecer um ícone que abre direto no seu dock,
sem barra de navegador.

## Deixar sempre rodando com o Windows (opcional)

1. Pressione `Win + R`, digite `shell:startup` e aperte Enter — abre a pasta de
   inicialização do Windows.
2. Copie um atalho do `dock-server.exe` para dentro dessa pasta.
3. Pronto: toda vez que o Windows ligar, o servidor sobe sozinho.

## Segurança

- O servidor só escuta na sua rede local — ninguém de fora consegue acessar,
  a não ser que você configure redirecionamento de porta no roteador (não faça
  isso sem necessidade).
- Ainda assim, mantenha o `token` só com você — quem tiver o link consegue abrir
  programas no seu PC.
- Para adicionar ou remover um app do dock, basta editar `config.json` e
  reiniciar o `dock-server.exe` — não precisa recompilar.
