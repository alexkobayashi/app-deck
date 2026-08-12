import java.util.Properties

/**
 * Material de assinatura, vindo de duas origens possíveis:
 *
 *  - `keystore.properties` na raiz de `android/` (build local, ignorado pelo git);
 *  - variáveis de ambiente (CI).
 *
 * Se nenhuma existir, o build de release cai na chave de debug. Isso é
 * deliberado: um clone limpo, um PR de terceiro e o CI comum precisam
 * conseguir rodar `assembleRelease` sem ter segredo nenhum — senão o R8 só
 * seria exercitado na hora de publicar, que é o pior momento para descobrir
 * que ele quebra a serialização.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyName: String, envName: String): String? =
    keystoreProperties.getProperty(propertyName) ?: System.getenv(envName)

val keystorePath = signingValue("storeFile", "ANDROID_KEYSTORE_PATH")
val keystorePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val keystoreAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
val keystoreKeyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")

/**
 * Assinar foi *pedido* se existe qualquer sinal de intenção — o arquivo de
 * propriedades ou uma variável de ambiente.
 */
val signingRequested = rootProject.file("keystore.properties").exists() ||
    listOf(keystorePath, keystorePassword, keystoreAlias, keystoreKeyPassword)
        .any { !it.isNullOrBlank() }

val missingSigningFields = mapOf(
    "storeFile / ANDROID_KEYSTORE_PATH" to keystorePath,
    "storePassword / ANDROID_KEYSTORE_PASSWORD" to keystorePassword,
    "keyAlias / ANDROID_KEY_ALIAS" to keystoreAlias,
    "keyPassword / ANDROID_KEY_PASSWORD" to keystoreKeyPassword,
).filterValues { it.isNullOrBlank() }.keys

val keystoreFile = keystorePath?.takeIf { it.isNotBlank() }?.let { file(it) }

// Assinatura pedida mas incompleta ou apontando para arquivo inexistente é
// erro, não motivo para cair na chave de debug. O fallback silencioso existia
// aqui e custou um APK publicado com a chave errada: o build passava, o APK
// parecia normal, e a falha só apareceria na hora de atualizar.
if (signingRequested) {
    if (missingSigningFields.isNotEmpty()) {
        error(
            "Assinatura de release configurada pela metade. Faltam: " +
                missingSigningFields.joinToString(", ") +
                ". Complete o android/keystore.properties (ou as variáveis de ambiente), " +
                "ou apague o arquivo para compilar com a chave de debug.",
        )
    }
    if (keystoreFile == null || !keystoreFile.exists()) {
        error(
            "A keystore não existe em '$keystorePath'. Confira o storeFile no " +
                "android/keystore.properties — o modelo em docs/release.md usa " +
                "SEU_USUARIO como espaço reservado, que precisa ser substituído. " +
                "Use barras normais (/), porque em .properties a barra invertida " +
                "é caractere de escape.",
        )
    }
}

val hasReleaseSigning = signingRequested

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.alexkobayashi.appdeck"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.alexkobayashi.appdeck"
        // minSdk 26 dá ícones adaptativos e Photo Picker nativo, cobrindo
        // praticamente todos os aparelhos em uso.
        minSdk = 26
        targetSdk = 37
        // Sobrescritos pelo workflow de release a partir da tag do git.
        versionCode = (providers.gradleProperty("appdeck.versionCode").orNull ?: "1").toInt()
        versionName = providers.gradleProperty("appdeck.versionName").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // A biblioteca nativa do ML Kit (libbarhopper_v3.so) pesa de 3 a 6
            // MB por arquitetura; incluindo as quatro, o APK ia de 2 para 23
            // MB. x86 e x86_64 existem só para emuladores, e ninguém instala
            // APK de release em emulador — o build de debug continua com todas
            // as arquiteturas, então o emulador segue funcionando.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // Sem chave própria, assina com a de debug: o APK continua
                // instalável e o build de qualquer clone funciona.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Desligado por padrão no AGP 8+; o app usa BuildConfig.DEBUG para
        // habilitar o log de rede só em debug.
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Room grava aqui o JSON do schema de cada versão. É o que permite testar
// migrações de verdade, em vez de confiar que estão certas.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// Sem jvmToolchain de propósito: assim o build funciona tanto com o JDK do
// Android Studio quanto com o JDK do CI, sem provisionar toolchain. Com o
// Kotlin embutido do AGP 9, o jvmTarget já herda de
// compileOptions.targetCompatibility (17 acima).

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.bundles.network)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.reorderable)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
