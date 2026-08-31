package com.example.riesgossocialesenchapinero.data

import android.content.Context
import android.content.SharedPreferences

enum class TemaApp {
    SISTEMA, CLARO, OSCURO
}

/**
 * Gestiona la persistencia de los ajustes de la aplicación.
 */
class AjustesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "barrio_seguro_ajustes",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val CLAVE_TEMA = "ajustes_tema"
        private const val CLAVE_IDIOMA = "ajustes_idioma"
    }

    var tema: TemaApp
        get() {
            val ordinal = prefs.getInt(CLAVE_TEMA, TemaApp.SISTEMA.ordinal)
            return TemaApp.entries.getOrElse(ordinal) { TemaApp.SISTEMA }
        }
        set(value) {
            prefs.edit().putInt(CLAVE_TEMA, value.ordinal).apply()
        }

    /**
     * Código de idioma (ej. "es", "en").
     * Si es nulo o vacío, se usa el del sistema.
     */
    var idioma: String
        get() = prefs.getString(CLAVE_IDIOMA, "") ?: ""
        set(value) {
            prefs.edit().putString(CLAVE_IDIOMA, value).apply()
        }
}
