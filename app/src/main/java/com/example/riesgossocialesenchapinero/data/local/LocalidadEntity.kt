package com.example.riesgossocialesenchapinero.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.riesgossocialesenchapinero.data.ApiClient

/**
 * Representación en base de datos del ranking de riesgo.
 * Permite que la app funcione offline mostrando los últimos datos descargados.
 */
@Entity(tableName = "localidades_cache")
data class LocalidadEntity(
    @PrimaryKey val posicion: Int,
    val nombre: String,
    val nivelRiesgo: String,
    val scorePonderado100k: Double,
    val tasaDelitos100k: Double,
    val timestampActualizacion: Long = System.currentTimeMillis()
) {
    fun aExternalModel() = ApiClient.Localidad(
        posicion = posicion,
        nombre = nombre,
        nivelRiesgo = nivelRiesgo,
        scorePonderado100k = scorePonderado100k,
        tasaDelitos100k = tasaDelitos100k
    )
}

fun ApiClient.Localidad.aEntity() = LocalidadEntity(
    posicion = posicion,
    nombre = nombre,
    nivelRiesgo = nivelRiesgo,
    scorePonderado100k = scorePonderado100k,
    tasaDelitos100k = tasaDelitos100k
)

@Dao
interface LocalidadDao {
    @Query("SELECT * FROM localidades_cache ORDER BY posicion ASC")
    suspend fun obtenerTodas(): List<LocalidadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarTodas(localidades: List<LocalidadEntity>)

    @Query("DELETE FROM localidades_cache")
    suspend fun borrarTodas()
}
