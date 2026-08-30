package com.example.riesgossocialesenchapinero.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MensajeDao {
    @Insert
    suspend fun insertar(mensaje: MensajeEntity): Long

    @Query("SELECT * FROM mensajes WHERE conversacionId = :conversacionId ORDER BY id ASC")
    suspend fun obtenerPorConversacion(conversacionId: Long): List<MensajeEntity>

    @Query("DELETE FROM mensajes WHERE conversacionId = :conversacionId")
    suspend fun borrarPorConversacion(conversacionId: Long)
}

@Dao
interface ConversacionDao {
    @Insert
    suspend fun insertar(conversacion: ConversacionEntity): Long

    @Query("SELECT * FROM conversaciones ORDER BY timestampMs DESC")
    fun observarTodas(): Flow<List<ConversacionEntity>>

    @Query("SELECT * FROM conversaciones ORDER BY timestampMs DESC")
    suspend fun obtenerTodas(): List<ConversacionEntity>

    @Query("UPDATE conversaciones SET titulo = :titulo WHERE id = :id")
    suspend fun actualizarTitulo(id: Long, titulo: String)

    // "Toca" la conversación para que suba al tope de la lista al enviar un
    // mensaje nuevo -- si no, el orden queda fijo por fecha de creación y una
    // conversación vieja pero activa no se distingue de una abandonada.
    @Query("UPDATE conversaciones SET timestampMs = :timestampMs WHERE id = :id")
    suspend fun tocar(id: Long, timestampMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversaciones WHERE id = :id")
    suspend fun borrar(id: Long)
}

@Dao
interface HechoDao {
    @Insert
    suspend fun insertar(hecho: HechoEntity): Long

    @Query("SELECT * FROM hechos_recordados ORDER BY id DESC")
    fun observarTodos(): Flow<List<HechoEntity>>

    @Query("SELECT texto FROM hechos_recordados ORDER BY id DESC")
    suspend fun obtenerTextos(): List<String>

    @Query("SELECT COUNT(*) FROM hechos_recordados WHERE texto = :texto")
    suspend fun existeTexto(texto: String): Int

    @Delete
    suspend fun borrar(hecho: HechoEntity)

    @Query("DELETE FROM hechos_recordados WHERE texto = :texto")
    suspend fun borrarPorTexto(texto: String)
}
