package dev.alexkobayashi.appdeck.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alexkobayashi.appdeck.domain.model.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Exercita o DataStore de verdade, apontado para um arquivo temporário — é o
 * construtor injetável que torna isso possível. Com a extension de Context,
 * todos os testes dividiriam o mesmo arquivo do processo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun dataStore(scope: TestScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.newFolder(), "settings.preferences_pb")
        }

    @Test
    fun `arquivo vazio significa acompanhar o sistema`() = runTest {
        val store = SettingsDataStore(dataStore(this))

        assertEquals(ThemeMode.System, store.themeMode.first())
    }

    @Test
    fun `grava e le de volta cada modo`() = runTest {
        val backing = dataStore(this)
        val store = SettingsDataStore(backing)

        for (mode in ThemeMode.entries) {
            store.setThemeMode(mode)
            assertEquals(mode, store.themeMode.first())
        }
    }

    @Test
    fun `chave desconhecida no disco cai no padrao em vez de estourar`() = runTest {
        val backing = dataStore(this)
        // Simula uma versão futura do app que gravou um modo que esta não
        // conhece, ou um arquivo corrompido.
        backing.edit { it[stringPreferencesKey("theme_mode")] = "arco-iris" }

        assertEquals(ThemeMode.System, SettingsDataStore(backing).themeMode.first())
    }
}
