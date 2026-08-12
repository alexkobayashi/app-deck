# App Deck — app Android

Deck de atalhos: uma grade de botões que, ao serem tocados, abrem programas no
PC com Windows onde o [servidor](../server/README.md) está rodando.

O contrato da API está em [docs/api.md](../docs/api.md).

## Compilar e instalar

Requer o **Android Studio**, que traz o JDK e o SDK. Dentro da IDE funciona
direto; para usar a linha de comando é preciso apontar as duas variáveis de
ambiente, porque o Android Studio **não** as define sozinho:

```powershell
# Uma vez, no PowerShell — vale a partir do próximo terminal
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
setx ANDROID_HOME "$env:LOCALAPPDATA\Android\Sdk"
```

Sem `JAVA_HOME`, o `gradlew` falha com *"JAVA_HOME is not set and no 'java'
command could be found in your PATH"*. Se ele estiver definido como string
vazia, o erro fica mais confuso (`'""' não é reconhecido...`) — o script monta
o caminho `""/bin/java.exe`.

Depois disso:

```bash
cd android
./gradlew :app:installDebug     # compila e instala no aparelho conectado
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Alternativamente, o Android Studio cria um `local.properties` apontando o SDK
ao abrir o projeto (o arquivo é ignorado pelo git).

### Memória

O `gradle.properties` usa heap enxuto (1,5 GB, sem paralelismo). Não é
descuido: o padrão sugerido pelo Android Studio (4 GB), somado aos workers de
lint, KSP e R8, derruba o daemon com *"insufficient memory"* numa máquina de
16 GB com a IDE aberta. Se precisar acelerar em uma máquina folgada, suba o
`org.gradle.jvmargs` localmente em vez de mudar o arquivo versionado.

## Primeiro uso

1. Rode o servidor no PC.
2. No app, toque na engrenagem e em **Escanear QR do servidor**.
3. Na bandeja do Windows, clique no ícone do App Deck → **Mostrar QR de
   pareamento**, e aponte a câmera.

O app valida o endereço com `GET /api/health` antes de salvar, então um QR de
outro app ou de um servidor que mudou de IP falha na hora do pareamento.

Se preferir configurar à mão, os mesmos campos (IP, porta, token) estão logo
abaixo — o IP aparece no log do servidor e o token está no `config.json`. O
botão **Testar conexão** verifica sem salvar nada.

O celular precisa estar na mesma rede Wi-Fi do PC, e a porta liberada no
Firewall do Windows para "Redes privadas".

## Usando o deck

- **Tocar** num atalho abre o programa no PC.
- **Tocar e segurar** abre o menu: trocar ícone ou editar o atalho.
- **+** na barra cria um atalho novo (gravado no `config.json` do PC).
- **Lápis** entra no modo de reorganização: arraste os tiles e toque em
  *Concluir*. A ordem é preferência local, guardada só no aparelho.

## Decisões de arquitetura

**Injeção de dependência manual** ([`AppContainer`](app/src/main/java/dev/alexkobayashi/appdeck/AppContainer.kt))
em vez de Hilt. Com um Activity e meia dúzia de singletons, o container
explícito é menos código que a configuração equivalente do Hilt e evita uma
segunda dependência de geração de código no build (o Room já usa KSP).

**Base URL dinâmica.** O Retrofit exige uma `baseUrl` fixa na construção, mas
o endereço do servidor muda em runtime. A `baseUrl` passada é um placeholder e
o `BaseUrlInterceptor` reescreve host e porta a cada requisição. Os
repositórios esperam a primeira leitura do DataStore (`awaitLoaded`) antes de
qualquer chamada, então o interceptor sempre encontra a configuração
carregada — sem `runBlocking` dentro de interceptor, que seria fonte fácil de
deadlock.

**O cache local é a fonte da UI.** A tela observa o Room; a rede só alimenta o
cache. Sem isso a grade abriria vazia e piscaria a cada abertura, e ficaria
inutilizável com o servidor desligado.

**Erros tipados.** `ApiError` distingue `NotConfigured`, `NoConnection`,
`Unauthorized`, `NotFound` e `Server`, porque cada um pede uma mensagem e uma
ação diferentes. A tradução para texto acontece na camada de UI, o que mantém
o estado livre de strings e os testes de ViewModel asseverando sobre tipos.

**Polling que para em background.** O `statusFlow` de conexão é um fluxo frio
coletado com `stateIn(WhileSubscribed)`; quando a tela sai de primeiro plano o
laço morre. O intervalo cresce (15s → 30s → 60s) depois de falhas
consecutivas: se o PC está desligado, insistir não ajuda.

## HTTP em texto claro

O `network_security_config.xml` permite cleartext e isso é intencional. O host
é um IP que o usuário digita ou escaneia, e `<domain-config>` não aceita faixas
de IP — só nomes de host específicos, que aqui não existem. O tráfego fica na
LAN e é autenticado por token no header.

O `allowBackup="false"` impede que o token saia do aparelho num backup do
Google. Quando o servidor ganhar HTTPS com certificado autoassinado, isso vira
`cleartextTrafficPermitted="false"` com um trust anchor próprio.

## Versões

Fixadas em [`gradle/libs.versions.toml`](gradle/libs.versions.toml). A matriz
AGP/Kotlin/KSP/Compose é rígida — subir uma versão por vez.

Dois pontos que não são óbvios:

- **Não existe o plugin `org.jetbrains.kotlin.android`.** A partir do AGP 9 o
  suporte a Kotlin é embutido no próprio AGP, e aplicar o plugin antigo é erro
  de build. A versão do Kotlin vem dos plugins de Compose e de serialização.
- **Kotlin 2.3.21, não 2.4.x.** O KSP passou a ter versionamento independente
  na 2.3.0 e a série publicada é a 2.3.x; manter o Kotlin na mesma linha evita
  apostar numa compatibilidade não verificada. O lint avisa que há versão mais
  nova — é esperado.

Sem `jvmToolchain`: o `jvmTarget` herda de `compileOptions.targetCompatibility`
(17), então o build funciona com o JDK do Android Studio e com o JDK 21 do CI
sem provisionar toolchain.

## Estrutura

```
app/src/main/java/dev/alexkobayashi/appdeck/
├── AppContainer.kt          # grafo de dependências
├── MainActivity.kt
├── data/
│   ├── local/               # Room: cache dos atalhos
│   ├── prefs/               # DataStore: IP, porta e token
│   ├── remote/              # Retrofit, interceptors, mapeamento de erro
│   └── repository/          # interfaces + implementações
├── domain/model/            # ServerConfig, DeckItem, ConnectionStatus
└── ui/
    ├── deck/                # grade de atalhos
    ├── serverconfig/        # configuração do servidor
    ├── common/              # tradução de ApiError para texto
    ├── navigation/
    └── theme/
```

## Fora do escopo desta versão

- **Pacote de ícones vetoriais embutidos.** Das três origens de ícone
  previstas no spec, emoji e galeria estão implementadas; o pacote embutido
  foi dispensado por dar muito trabalho para pouca diferença prática. O
  modelo já suporta (`ShortcutIcon.Builtin`, `IconType.BUILTIN`), então
  acrescentar depois não exige migração de banco.
- **Recorte manual da imagem.** A foto escolhida é recortada no centro
  automaticamente.
- **Mais de um servidor.** A coluna `server_key` já existe reservada na
  tabela de customização, para não exigir migração dolorosa depois.
- **Persistir a ordem no servidor.** Hoje é preferência local; o endpoint
  `PUT /api/apps/order` está reservado em `docs/api.md`.
