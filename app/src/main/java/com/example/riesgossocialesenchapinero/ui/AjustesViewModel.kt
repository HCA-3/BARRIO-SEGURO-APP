package com.example.riesgossocialesenchapinero.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import com.example.riesgossocialesenchapinero.data.AjustesManager
import com.example.riesgossocialesenchapinero.data.TemaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AjustesUiState(
    val tema: TemaApp = TemaApp.SISTEMA,
    val idioma: String = "", // "" para sistema
    val intervaloMinutos: Int = 2,
    val alertasHabilitadas: Boolean = true
)

/**
 * ViewModel para gestionar los ajustes globales de la aplicación.
 */
class AjustesViewModel(application: Application) : AndroidViewModel(application) {
    private val ajustesManager = AjustesManager(application)
    
    private val _estado = MutableStateFlow(
        AjustesUiState(
            tema = ajustesManager.tema,
            idioma = ajustesManager.idioma,
            intervaloMinutos = ajustesManager.intervaloMinutos,
            alertasHabilitadas = ajustesManager.alertasHabilitadas
        )
    )
    val estado: StateFlow<AjustesUiState> = _estado

    init {
        // Asegurar que el idioma guardado se aplique al iniciar
        if (ajustesManager.idioma.isNotEmpty()) {
            val appLocales = LocaleListCompat.forLanguageTags(ajustesManager.idioma)
            if (AppCompatDelegate.getApplicationLocales() != appLocales) {
                AppCompatDelegate.setApplicationLocales(appLocales)
            }
        }
    }

    fun cambiarTema(nuevoTema: TemaApp) {
        ajustesManager.tema = nuevoTema
        _estado.value = _estado.value.copy(tema = nuevoTema)
    }

    fun cambiarIdioma(nuevoIdioma: String) {
        ajustesManager.idioma = nuevoIdioma
        _estado.value = _estado.value.copy(idioma = nuevoIdioma)
        
        val appLocales = if (nuevoIdioma.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(nuevoIdioma)
        }
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun cambiarIntervalo(minutos: Int) {
        ajustesManager.intervaloMinutos = minutos
        _estado.value = _estado.value.copy(intervaloMinutos = minutos)
    }

    fun cambiarAlertas(habilitadas: Boolean) {
        ajustesManager.alertasHabilitadas = habilitadas
        _estado.value = _estado.value.copy(alertasHabilitadas = habilitadas)
    }
}
