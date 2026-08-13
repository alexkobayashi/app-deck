# Receitas de atalhos

Um atalho do deck é só um par **`path` + `args`**. O servidor entrega os dois
direto para `exec.Command(path, args...)` (`server/internal/launcher`) — não
há shell no meio. Isso é simples e previsível, mas significa que várias
coisas que funcionam quando você digita no Executar (`Win+R`) **não**
funcionam aqui.

Este arquivo junta o que funciona, o que não funciona e por quê.

## Regras que valem para todo atalho

- **`path` tem que ser um arquivo executável que existe.** O launcher faz
  `os.Stat` antes de executar; caminho inexistente ou diretório vira erro
  claro na resposta da API.
- **Argumentos são separados por espaço simples, sem aspas.** O campo de
  argumentos do app quebra a string em espaços
  (`ShortcutEditorViewModel.parseArgs`). Um argumento que contém espaço —
  `--profile-directory=Profile 1`, um caminho de arquivo com espaço — vira
  dois argumentos e não funciona. Limitação conhecida da v1.
- **O processo filho sobe com `DETACHED_PROCESS`**, ou seja, sem console
  próprio (`server/internal/launcher/platform_windows.go`). Programas
  gráficos abrem normalmente; **programas de console abrem invisíveis**.
- **O diretório de trabalho vira a pasta do executável**, porque muitos
  programas do Windows procuram arquivos ao lado de si mesmos.
- **Atalhos `.lnk` não funcionam como `path`.** Quem resolve `.lnk` é o
  `ShellExecute`, não o `CreateProcess` que o Go usa. O `os.Stat` passa (o
  arquivo existe), mas a execução falha. Aponte sempre para o alvo do
  atalho, nunca para o atalho.
- **Caracteres invisíveis no caminho são removidos automaticamente.** A caixa
  de *Propriedades* do Windows copia o caminho com um `U+202A`
  (LEFT-TO-RIGHT EMBEDDING) na frente. Ele não aparece em lugar nenhum — nem
  no campo do app, nem no `config.json`, nem na mensagem de erro — e fazia o
  `os.Stat` falhar com "executável não encontrado" num caminho visivelmente
  perfeito.

  O servidor agora limpa esses caracteres ao carregar o config e ao receber
  um `POST`/`PUT`, e registra um aviso nomeando o code point:

  ```
  level=WARN msg=configuração aviso="app abcdef0123456789 (path): caractere
  invisível (U+202A) removido do path \"\u202aC:\\Windows\\System32\\calc.exe\";
  costuma vir de copiar o caminho pela caixa de Propriedades do Windows"
  ```

  Um `config.json` que já tenha o problema **se cura no próximo start** — o
  arquivo é regravado limpo, com os `id` preservados (nenhum ícone
  customizado é perdido). Só o `path` passa por essa limpeza; o `name` é
  texto livre, onde marcas bidirecionais podem ser intencionais.

## Programa comum

O caso padrão.

| Campo | Valor |
|---|---|
| `path` | `C:\Program Files\Google\Chrome\Application\chrome.exe` |
| `args` | *(vazio)* |

## Aplicativo da Microsoft Store (UWP)

Spotify, Netflix, Fotos, Calculadora — apps da Store **não abrem pelo
`.exe`**, por dois motivos que se somam:

1. O executável fica em `C:\Program Files\WindowsApps\`, protegido por ACL.
   O `os.Stat` do launcher passa (o arquivo existe), mas o `CreateProcess`
   devolve **"Acesso negado"**.
2. Mais de fundo: um app UWP precisa ser *ativado* pelo modelo de app do
   Windows, não simplesmente executado. Nem com permissão o caminho direto
   seria o jeito certo.

A saída é pedir ao shell que ative o app pelo **AUMID** (*Application User
Model ID*):

| Campo | Valor |
|---|---|
| `path` | `C:\Windows\explorer.exe` |
| `args` | `shell:AppsFolder\<PackageFamilyName>!<AppId>` |

Exemplo real, do Spotify:

| Campo | Valor |
|---|---|
| `path` | `C:\Windows\explorer.exe` |
| `args` | `shell:AppsFolder\SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify` |

Para descobrir o AUMID de qualquer app instalado:

```powershell
Get-StartApps | Where-Object { $_.Name -like "*Spotify*" }
```

A coluna `AppID` é exatamente o que vai depois de `shell:AppsFolder\`. Use
esse comando em vez de montar o AUMID à mão: um pacote pode ter vários
pontos de entrada (o do Spotify tem cinco — `Spotify`, `SpotifyCli`,
`SpotifyLauncher`, `SpotifyWidgetProvider`, `Widget`) e o `Get-StartApps` já
devolve aquele que o Menu Iniciar usa.

Por que essa receita se encaixa bem no deck:

- O AUMID **não tem espaço**, então sobrevive ao parser de argumentos da v1.
- O `explorer.exe` delega para o shell e encerra na hora. O launcher não se
  incomoda, porque usa `cmd.Start()` e não espera o filho terminar.
- Diferente do `--app=` do Chrome, tocar de novo **reaproveita** a janela já
  aberta, porque quem decide é o próprio modelo de app do Windows.

A primeira abertura do dia leva alguns segundos: é o cold start do app UWP,
não do deck. A API já respondeu `{"status":"launched"}` bem antes da janela
aparecer.

## Página web

### Janela dedicada, sem barra de endereço (recomendado)

O jeito mais limpo de colocar um site no deck. Não precisa instalar nada.

| Campo | Valor |
|---|---|
| `path` | `C:\Program Files\Google\Chrome\Application\chrome.exe` |
| `args` | `--app=https://chat.google.com/app/home` |

A janela abre sem abas e sem barra de endereço, com cara de aplicativo.
**Cada toque abre uma janela nova** — o `--app=` não reaproveita uma janela
já aberta. Se isso incomodar, use a receita de PWA instalado abaixo.

### Aba normal do navegador

| Campo | Valor |
|---|---|
| `path` | `C:\Program Files\Google\Chrome\Application\chrome.exe` |
| `args` | `https://calendar.google.com` |

Adicione `--new-window` antes da URL para forçar uma janela nova em vez de
uma aba na janela existente.

> A URL vai sempre em `args`. Colocar `https://...` no campo `path` não
> funciona — o launcher espera um arquivo, e a validação recusa.

### Navegador padrão do sistema

Quando você não quer fixar o Chrome. É o mesmo mecanismo que o servidor usa
para abrir o QR de pareamento (`server/internal/pairing/open_windows.go`).

| Campo | Valor |
|---|---|
| `path` | `C:\Windows\System32\rundll32.exe` |
| `args` | `url.dll,FileProtocolHandler https://calendar.google.com` |

## Aplicativo "instalado" pelo Chrome (PWA)

Quando você usa *Instalar* no Chrome, ele cria um `.lnk` em
`%APPDATA%\Microsoft\Windows\Start Menu\Programs\Chrome Apps\`. Esse `.lnk`
**não serve** como `path` (veja as regras acima) — o que interessa é o alvo
dele, que tem sempre este formato:

| Campo | Valor |
|---|---|
| `path` | `C:\Program Files\Google\Chrome\Application\chrome_proxy.exe` |
| `args` | `--profile-directory=Default --app-id=kjbdgfilnfhdoflbpgamdcdgpehopbep` |

Vantagem sobre o `--app=`: ícone e identidade próprios na barra de tarefas, e
a janela existente é reaproveitada em vez de abrir outra.

Para descobrir o `--app-id` de um app já instalado:

```powershell
$sh = New-Object -ComObject WScript.Shell
Get-ChildItem "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Chrome Apps\*.lnk" |
  ForEach-Object { $l = $sh.CreateShortcut($_.FullName)
    [PSCustomObject]@{ Nome=$_.BaseName; Path=$l.TargetPath; Args=$l.Arguments } } | Format-List
```

Copie `Path` e `Args` direto para o atalho. **Cuidado com o perfil:** se o app
estiver num perfil chamado `Profile 1`, o espaço quebra o parser de
argumentos da v1 e não há como contornar — só funciona no `Default`.

## Terminal como administrador

O servidor roda **sem elevação** (sobe pelo `HKCU\...\Run`), e no Windows um
processo não elevado não consegue criar um processo elevado via
`CreateProcess`. Não existe argumento que contorne isso — é fronteira de
segurança do sistema operacional.

### Tarefa agendada — sem UAC, funciona pelo celular

Uma vez, num terminal **administrador** no PC:

```cmd
schtasks /create /tn CmdAdmin /tr "cmd.exe /k" /rl HIGHEST /sc ONCE /st 00:00 /f
```

Depois, o atalho:

| Campo | Valor |
|---|---|
| `path` | `C:\Windows\System32\schtasks.exe` |
| `args` | `/run /tn CmdAdmin` |

O `/rl HIGHEST` dá token elevado sem prompt, e como a tarefa pertence ao
usuário logado a janela aparece na sua sessão. Requer conta de administrador.
Use nome de tarefa **sem espaço** — o parser de argumentos da v1 não entende
aspas.

Essa receita também resolve o problema do console invisível: quem cria a
janela é o Agendador de Tarefas, não o launcher.

### Alternativa com UAC

| Campo | Valor |
|---|---|
| `path` | `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` |
| `args` | `-Command Start-Process cmd -Verb RunAs` |

Funciona, mas o diálogo do UAC abre na *secure desktop* do PC e exige clique
físico — não dá para aprovar pelo celular.

### O que não fazer

Rodar o servidor inteiro elevado (autostart por tarefa agendada com
privilégios mais altos) faria todo atalho herdar o token de administrador e
resolveria tudo isso. **Não faça** sem pensar bem: a v1 é HTTP puro, sem TLS,
e quem tiver o token na rede local passaria a ter execução de código como
administrador na máquina.

## Resumo do que não funciona

| Tentativa | Por quê |
|---|---|
| `path` = arquivo `.lnk` | `CreateProcess` não resolve atalhos do Windows |
| `path` = `.exe` em `WindowsApps` | ACL nega o acesso; app UWP precisa ser ativado, não executado |
| `path` = `https://...` | o launcher exige um arquivo existente |
| Argumento com espaço | o parser da v1 quebra a string em espaços |
| `path` = `cmd.exe` direto | `DETACHED_PROCESS` deixa o console invisível |
| Elevar direto no atalho | UAC — processo não elevado não cria filho elevado |

## Onde isso está no código

| Comportamento | Arquivo |
|---|---|
| Execução do atalho, validação do `path` | `server/internal/launcher/launcher.go` |
| Flags de criação de processo | `server/internal/launcher/platform_windows.go` |
| Campos `path` e `args` na API | `docs/api.md`, `server/internal/httpapi/handlers_apps.go` |
| Parser do campo de argumentos no app | `android/.../ui/editor/ShortcutEditorViewModel.kt` |
