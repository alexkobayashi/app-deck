package dev.alexkobayashi.appdeck.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CachedAppEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(StringListConverter::class)
abstract class AppDeckDatabase : RoomDatabase() {

    abstract fun cachedAppDao(): CachedAppDao

    companion object {
        fun create(context: Context): AppDeckDatabase =
            Room.databaseBuilder(context, AppDeckDatabase::class.java, "app-deck.db")
                // O conteúdo é cache reconstruível a partir do servidor, então
                // uma migração malfeita não deve travar o app: apagar e
                // recarregar é sempre seguro aqui. A tabela de customização de
                // ícones, na próxima fase, vai exigir migração de verdade.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
