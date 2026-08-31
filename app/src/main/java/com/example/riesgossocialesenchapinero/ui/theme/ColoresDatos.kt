package com.example.riesgossocialesenchapinero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colores de los datos (barras, badges de riesgo).
 *
 * Van fijos y NO salen del MaterialTheme a propósito: el tema usa dynamic color
 * (Material You), así que la paleta cambia con el fondo de pantalla del celular.
 * Eso está bien para la interfaz, pero no para los datos, donde "rojo = riesgo
 * alto" tiene que significar siempre lo mismo y el contraste tiene que ser
 * predecible en vez de depender del wallpaper de cada quien.
 *
 * Contrastes WCAG medidos (no estimados) del relleno contra cada tinta:
 *   bajo   #0ca30c -> blanco 3.35 · oscuro 5.68  => tinta oscura
 *   medio  #fab219 -> blanco 1.83 · oscuro 10.38 => tinta oscura (blanco era ilegible)
 *   alto   #d03b3b -> blanco 4.80 · oscuro 3.96  => tinta blanca
 *
 * El nivel se acompaña SIEMPRE del texto ("ALTO"/"MEDIO"/"BAJO"): el color es
 * refuerzo, nunca el único portador del significado — que es lo que lo hace
 * legible también para daltonismo.
 */
object ColoresDatos {
    private val BAJO = Color(0xFF0CA30C)
    private val MEDIO = Color(0xFFFAB219)
    private val ALTO = Color(0xFFD03B3B)

    private val TINTA_OSCURA = Color(0xFF10100F)
    private val TINTA_CLARA = Color.White

    /** Azul de una sola tonalidad para magnitud (los delitos son una sola serie). */
    private val BARRA_CLARO = Color(0xFF2A78D6) // 4.30:1 sobre superficie clara
    private val BARRA_OSCURO = Color(0xFF3987E5) // 4.79:1 sobre superficie oscura

    fun relleno(nivel: String): Color = when (nivel.lowercase()) {
        "alto" -> ALTO
        "medio" -> MEDIO
        else -> BAJO
    }

    /** La tinta que mejor contrasta sobre [relleno], según lo medido arriba. */
    fun tinta(nivel: String): Color =
        if (nivel.lowercase() == "alto") TINTA_CLARA else TINTA_OSCURA

    @Composable
    @ReadOnlyComposable
    fun barra(): Color = if (isSystemInDarkTheme()) BARRA_OSCURO else BARRA_CLARO
}
