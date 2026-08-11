package dev.alexkobayashi.appdeck.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedAppEntity::class, ShortcutCustomizationEntity::class],
    version = 2,
    // Schema versionado em app/schemas: é o que permite escrever e testar
    // migrações com confiança.
    exportSchema = true,
)
@TypeConverters(StringListConverter::class)
abstract class AppDeckDatabase : RoomDatabase() {

    abstract fun cachedAppDao(): CachedAppDao

    abstract fun customizationDao(): ShortcutCustomizationDao

    companion object {
        /**
         * v1 -> v2: acrescenta a tabela de customização.
         *
         * Migração explícita, e não fallback destrutivo: a partir daqui o
         * banco guarda o ícone que o usuário escolheu, que não pode ser
         * reconstruído a partir do servidor.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shortcut_customization` (
                        `app_id` TEXT NOT NULL,
                        `server_key` TEXT NOT NULL,
                        `icon_type` TEXT NOT NULL,
                        `icon_ref` TEXT,
                        `sort_order` INTEGER,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`app_id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): AppDeckDatabase =
            Room.databaseBuilder(context, AppDeckDatabase::class.java, "app-deck.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
