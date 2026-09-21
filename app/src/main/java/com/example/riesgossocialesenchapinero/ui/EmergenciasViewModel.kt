package com.example.riesgossocialesenchapinero.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmergenciasUiState(
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

