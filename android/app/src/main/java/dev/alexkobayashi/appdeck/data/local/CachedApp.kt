package dev.alexkobayashi.appdeck.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Cópia local da lista de atalhos vinda de `GET /api/apps`.
 *
 * Sem esse cache o deck abriria vazio e piscaria a cada abertura, e ficaria
 * inutilizável fora de casa. Com ele, a grade aparece na hora e continua
 * visível mesmo com o servidor desligado — só o toque falha, com mensagem.
 */
@Entity(tableName = "cached_app")
data class CachedAppEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    /** Argumentos serializados como JSON — ver [StringListConverter]. */
    val args: String,
    /** Posição no array devolvido pelo servidor: é a ordem do deck. */
    @ColumnInfo(name = "server_order") val serverOrder: Int,
)

class StringListConverter {
    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String): List<String> = try {
        Json.decodeFromString(value)
    } catch (e: Exception) {
        emptyList()
    }
}

@Dao
interface CachedAppDao {

    @Query("SELECT * FROM cached_app ORDER BY server_order ASC")
    fun observeAll(): Flow<List<CachedAppEntity>>

    @Query("SELECT * FROM cached_app ORDER BY server_order ASC")
    suspend fun getAll(): List<CachedAppEntity>

    @Query("DELETE FROM cached_app")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(apps: List<CachedAppEntity>)

    /**
     * Substitui o cache inteiro numa transação.
     *
     * Apagar e inserir separadamente deixaria a UI ver, por um instante, um
     * deck vazio.
     */
    @Transaction
    suspend fun replaceAll(apps: List<CachedAppEntity>) {
        deleteAll()
        insertAll(apps)
    }
}
