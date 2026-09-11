package com.example.riesgossocialesenchapinero.ui

import android.app.Application
import com.example.riesgossocialesenchapinero.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.data.local.AppDatabase
import com.example.riesgossocialesenchapinero.data.local.aEntity
import com.example.riesgossocialesenchapinero.data.local.LocalidadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RiesgoUiState {
    object Cargando : RiesgoUiState
    data class Listo(val localidades: List<ApiClient.Localidad>, val esCache: Boolean = false) : RiesgoUiState
    data class Error(val mensaje: String) : RiesgoUiState
}

class RiesgoViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.obtener(application)
    private val localidadDao = db.localidadDao()

    private val _estado = MutableStateFlow<RiesgoUiState>(RiesgoUiState.Cargando)
    val estado: StateFlow<RiesgoUiState> = _estado

    init {
        cargarRanking()
    }

    fun cargarRanking() {
        viewModelScope.launch {
            // 1. Cargar inmediatamente desde la caché local o del fallback oficial si la DB está vacía
            val cacheLocal = withContext(Dispatchers.IO) { localidadDao.obtenerTodas() }
            val localidadesIniciales = if (cacheLocal.isNotEmpty()) {
                cacheLocal.map { it.aExternalModel() }
            } else {
                val fallback = ApiClient.obtenerRankingFallbackOficial()
                withContext(Dispatchers.IO) {
                    localidadDao.insertarTodas(fallback.map { it.aEntity() })
                }
                fallback
            }

            // Publicar datos inmediatamente para que la UI no se bloquee ni muestre loaders innecesarios
            _estado.value = RiesgoUiState.Listo(localidadesIniciales, esCache = true)

            // 2. Intentar actualizar asíncronamente desde el servidor en segundo plano
            try {
                val rankingServidor = withContext(Dispatchers.IO) { ApiClient.obtenerRanking() }
                withContext(Dispatchers.IO) {
                    localidadDao.borrarTodas()
                    localidadDao.insertarTodas(rankingServidor.map { it.aEntity() })
                }
                _estado.value = RiesgoUiState.Listo(rankingServidor, esCache = false)
            } catch (_: Exception) {
                // Si el servidor está inalcanzable, se mantienen los datos cargados previamente de forma transparente
            }
        }
    }

    /** Fija la URL que el usuario escribió y recarga. */
    fun cambiarServidor(url: String) {
        ApiClient.baseUrl = url
        cargarRanking()
    }
}
