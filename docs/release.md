# Publicar uma versão

Uma tag `v*` empacota **os dois** artefatos no mesmo GitHub Release: o `.exe`
do servidor e o `.apk` do app.

```powershell
git tag v0.4.0
git push origin v0.4.0
```

O workflow [`release.yml`](../.github/workflows/release.yml) roda os testes,
compila e publica. O job do Android depende do job do servidor, para os dois
não criarem o mesmo Release em paralelo.

O `versionName` do app vem da tag (sem o `v`) e o `versionCode` do número da
execução do workflow — sempre crescente, que é o que o Android exige para
aceitar uma atualização.

---

## Assinatura do APK — configuração única

Sem isso o APK ainda é publicado, mas assinado com a **chave de debug**. Isso
funciona para instalar, e é o que acontece hoje. O problema aparece depois:
um APK assinado com outra chave **não instala por cima** de uma instalação
existente — é preciso desinstalar e perder os dados do app (os ícones
escolhidos, a configuração do servidor).

Ou seja: configure a assinatura **antes** da primeira versão que você
pretenda atualizar depois.

### 1. Gerar a keystore

O `keytool` vem com o JDK do Android Studio:

```powershell
& "$env:JAVA_HOME\bin\keytool" -genkeypair -v `
  -keystore "$env:USERPROFILE\Documents\appdeck-release.jks" `
  -alias appdeck `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storetype PKCS12
```

Ele pede uma senha e alguns dados (nome, organização, cidade). Os dados podem
ser o que você quiser — não são verificados por ninguém.

> **Guarde essa keystore e a senha fora do repositório e faça backup.**
> Perder o arquivo ou a senha significa nunca mais conseguir publicar uma
> atualização que instale por cima da anterior. Não existe recuperação.

O caminho sugerido (`Documents`) está fora do repositório de propósito. O
`.gitignore` já bloqueia `*.jks`, `*.keystore` e `keystore.properties`, mas a
melhor proteção é o arquivo não estar lá.

### 2. Build de release na sua máquina (opcional)

Crie `android/keystore.properties` — ignorado pelo git:

```properties
storeFile=C:/Users/SEU_USUARIO/Documents/appdeck-release.jks
storePassword=SUA_SENHA
keyAlias=appdeck
keyPassword=SUA_SENHA
```

Use barras normais (`/`) mesmo no Windows: é um arquivo `.properties`, onde a
barra invertida é caractere de escape.

```powershell
cd android
.\gradlew :app:assembleRelease
```

O APK sai em `app/build/outputs/apk/release/app-release.apk`.

### 3. Secrets no GitHub

Converta a keystore para base64:

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("$env:USERPROFILE\Documents\appdeck-release.jks")
) | Set-Clipboard
```

Em **Settings → Secrets and variables → Actions → New repository secret**,
crie os quatro:

| Secret | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | O conteúdo copiado acima |
| `ANDROID_KEYSTORE_PASSWORD` | A senha da keystore |
| `ANDROID_KEY_ALIAS` | `appdeck` |
| `ANDROID_KEY_PASSWORD` | A senha da chave (a mesma, se você usou uma só) |

O workflow decodifica a keystore para um arquivo temporário do runner e o
apaga ao final, inclusive se o build falhar.

### 4. Publicar

```powershell
git tag v0.4.0
git push origin v0.4.0
```

Confira em **Actions** que os dois jobs passaram, e no Release que estão lá o
`.zip` do servidor, o `.apk` e os dois `SHA256SUMS`.

---

## Por que o build de release funciona sem os secrets

O Gradle procura o material de assinatura em `keystore.properties` e depois
nas variáveis de ambiente; não achando, usa a chave de debug.

Isso é deliberado. Um clone limpo, um PR de terceiro e o CI comum precisam
conseguir rodar `assembleRelease` sem segredo nenhum — é assim que o **R8** é
exercitado a cada push. Sem isso, a minificação só seria testada na hora de
publicar, que é o pior momento para descobrir que ela quebrou a
serialização dos DTOs.

## Avisos esperados na instalação

- **SmartScreen** no `.exe`: o binário não é assinado com certificado de
  código (isso é outra coisa, paga e anual). "Mais informações" → "Executar
  assim mesmo".
- **"Instalar apps desconhecidos"** no APK: o Android pede autorização para o
  aplicativo de onde veio o arquivo (navegador ou gerenciador de arquivos).

Ambos são inerentes a distribuir fora das lojas e não têm solução gratuita.
