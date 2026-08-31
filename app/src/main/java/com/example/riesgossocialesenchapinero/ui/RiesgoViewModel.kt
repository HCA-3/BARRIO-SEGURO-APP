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
        _estado.value = RiesgoUiState.Cargando
        viewModelScope.launch {
            try {
                // 1. Intentar descargar del backend
                val ranking = withContext(Dispatchers.IO) { ApiClient.obtenerRanking() }
                
                // 2. Si hay éxito, guardar en caché local
                withContext(Dispatchers.IO) {
                    localidadDao.borrarTodas()
                    localidadDao.insertarTodas(ranking.map { it.aEntity() })
                }
                
                _estado.value = RiesgoUiState.Listo(ranking, esCache = false)
            } catch (e: Exception) {
                // 3. Si falla, intentar cargar de la caché local
                val cache = withContext(Dispatchers.IO) { localidadDao.obtenerTodas() }
                
                if (cache.isNotEmpty()) {
                    _estado.value = RiesgoUiState.Listo(
                        localidades = cache.map { it.aExternalModel() },
                        esCache = true
                    )
                } else {
                    _estado.value = RiesgoUiState.Error(
                        getApplication<Application>().getString(R.string.error_no_conexion_no_cache)
                    )
                }
            }
        }
    }

    /** Fija la URL que el usuario escribió y recarga. */
    fun cambiarServidor(url: String) {
        ApiClient.baseUrl = url
        cargarRanking()
    }
}
