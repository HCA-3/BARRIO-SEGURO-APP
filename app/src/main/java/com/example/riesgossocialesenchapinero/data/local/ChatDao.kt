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

    @Query("SELECT * FROM mensajes ORDER BY id ASC")
    suspend fun obtenerTodos(): List<MensajeEntity>

    @Query("DELETE FROM mensajes")
    suspend fun borrarTodos()
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
