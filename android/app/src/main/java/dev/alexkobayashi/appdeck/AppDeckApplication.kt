package dev.alexkobayashi.appdeck

import android.app.Application

/**
 * Ponto de entrada do processo e dono do grafo de dependências.
 *
 * A injeção é manual, via [AppContainer], em vez de Hilt: para um app com um
 * Activity e um punhado de singletons, o container explícito é menos código
 * total que a configuração do Hilt e elimina uma dependência de geração de
 * código do build.
 */
class AppDeckApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
