package com.example.riesgossocialesenchapinero.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.riesgossocialesenchapinero.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EmergenciasUiState(
    val sismos: List<ApiClient.Sismo> = emptyList(),
    val cargandoSismos: Boolean = false,
    val errorSismos: String? = null,
    val mochilaChecklist: Set<String> = emptySet(),
    val ultimoRefresco: Long = 0L,
)

class EmergenciasViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("barrio_seguro_emergencias", Context.MODE_PRIVATE)
    private val CLAVE_MOCHILA = "mochila_items"

    private val _estado = MutableStateFlow(
        EmergenciasUiState(
            mochilaChecklist = prefs.getStringSet(CLAVE_MOCHILA, emptySet()) ?: emptySet()
        )
    )
    val estado: StateFlow<EmergenciasUiState> = _estado.asStateFlow()

    init {
        cargarSismos()
    }

    fun cargarSismos() {
        _estado.value = _estado.value.copy(cargandoSismos = true, errorSismos = null)
        viewModelScope.launch {
            try {
                val lista = withContext(Dispatchers.IO) {
                    ApiClient.obtenerSismosRecientes()
                }
                _estado.value = _estado.value.copy(
                    sismos = lista,
                    cargandoSismos = false,
                    ultimoRefresco = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    cargandoSismos = false,
                    errorSismos = e.message ?: "No se pudieron actualizar los sismos"
                )
            }
        }
    }

    fun toggleItemMochila(idItem: String) {
        val actual = _estado.value.mochilaChecklist.toMutableSet()
        if (actual.contains(idItem)) {
            actual.remove(idItem)
        } else {
            actual.add(idItem)
        }
        prefs.edit().putStringSet(CLAVE_MOCHILA, actual).apply()
        _estado.value = _estado.value.copy(mochilaChecklist = actual)
    }
}
