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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Ainda não existe keystore de release: assinar com a chave de
            // debug mantém o APK instalável e o build de qualquer clone
            // funcionando. A fase de release troca por uma chave própria.
            signingConfig = signingConfigs.getByName("debug")
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
