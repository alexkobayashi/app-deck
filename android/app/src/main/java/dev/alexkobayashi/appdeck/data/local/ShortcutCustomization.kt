package dev.alexkobayashi.appdeck.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.alexkobayashi.appdeck.domain.model.IconType
import kotlinx.coroutines.flow.Flow

/**
 * Customização local de um atalho: o ícone escolhido e a posição no deck.
 *
 * Diferente de [CachedAppEntity], isto **não é cache** — é dado que só existe
 * no aparelho e que o usuário perderia para sempre se fosse apagado. Daí as
 * migrações explícitas do banco, sem fallback destrutivo.
 */
@Entity(tableName = "shortcut_customization")
data class ShortcutCustomizationEntity(
    /** O id do atalho no servidor. Estável, por isso serve de chave. */
    @PrimaryKey @ColumnInfo(name = "app_id") val appId: String,

    /**
     * Reservado para quando o app suportar mais de um servidor. Já existe
     * como coluna para não exigir uma migração dolorosa depois.
     */
    @ColumnInfo(name = "server_key") val serverKey: String = DEFAULT_SERVER_KEY,

    @ColumnInfo(name = "icon_type") val iconType: IconType = IconType.NONE,

    /**
     * Depende do [iconType]: o caractere do emoji, a chave estável do ícone
     * embutido, ou o nome do arquivo da imagem em `filesDir/icons`.
     */
    @ColumnInfo(name = "icon_ref") val iconRef: String? = null,

    @ColumnInfo(name = "sort_order") val sortOrder: Int? = null,

    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_SERVER_KEY = "default"
    }
}

@Dao
interface ShortcutCustomizationDao {

    @Query("SELECT * FROM shortcut_customization")
    fun observeAll(): Flow<List<ShortcutCustomizationEntity>>

    @Query("SELECT * FROM shortcut_customization WHERE app_id = :appId")
    suspend fun findByAppId(appId: String): ShortcutCustomizationEntity?

    @Upsert
    suspend fun upsert(entity: ShortcutCustomizationEntity)

    @Query("DELETE FROM shortcut_customization WHERE app_id = :appId")
    suspend fun deleteByAppId(appId: String)

    @Query(
        """
        SELECT * FROM shortcut_customization
        WHERE app_id NOT IN (SELECT id FROM cached_app)
        """,
    )
    suspend fun findOrphans(): List<ShortcutCustomizationEntity>

    /**
     * Grava a ordem de todos os atalhos numa transação.
     *
     * Em bloco porque a reordenação é uma operação só do ponto de vista do
     * usuário: um estado intermediário com duas posições iguais faria a grade
     * piscar embaralhada.
     */
    @Transaction
    suspend fun saveOrder(orderedAppIds: List<String>) {
        orderedAppIds.forEachIndexed { index, appId ->
            val existing = findByAppId(appId)
            if (existing != null) {
                upsert(existing.copy(sortOrder = index))
            } else {
                upsert(ShortcutCustomizationEntity(appId = appId, sortOrder = index))
            }
        }
    }
}
