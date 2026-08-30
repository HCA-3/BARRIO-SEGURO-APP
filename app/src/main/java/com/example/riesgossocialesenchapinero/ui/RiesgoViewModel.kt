package com.example.riesgossocialesenchapinero.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.riesgossocialesenchapinero.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface RiesgoUiState {
    object Cargando : RiesgoUiState
    data class Listo(val localidades: List<ApiClient.Localidad>) : RiesgoUiState
    data class Error(val mensaje: String) : RiesgoUiState
}

class RiesgoViewModel : ViewModel() {
    private val _estado = MutableStateFlow<RiesgoUiState>(RiesgoUiState.Cargando)
    val estado: StateFlow<RiesgoUiState> = _estado

    init {
        cargarRanking()
    }

    fun cargarRanking() {
        _estado.value = RiesgoUiState.Cargando
        viewModelScope.launch {
            _estado.value = try {
                // ApiClient ya reintenta solo con autodetección si la URL
                // guardada dejó de responder.
                val ranking = withContext(Dispatchers.IO) { ApiClient.obtenerRanking() }
                RiesgoUiState.Listo(ranking)
            } catch (e: Exception) {
                RiesgoUiState.Error(
                    "No respondió ninguna de estas direcciones: " +
                        ApiClient.CANDIDATOS.joinToString() + ". Detalle: " +
                        (e.message ?: "error desconocido"),
                )
            }
        }
    }

    /** Fija la URL que el usuario escribió y recarga. */
    fun cambiarServidor(url: String) {
        ApiClient.baseUrl = url
        cargarRanking()
    }
}
