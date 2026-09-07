package com.example.riesgossocialesenchapinero.util

import java.util.regex.Pattern

/**
 * Filtro automático de moderación de lenguaje para el Chat Global Anónimo.
 * Censura cualquier palabra soez u ofensiva reemplazándola por '****'.
 */
object FiltroGroserias {

    private val PALABRAS_OFENSIVAS = listOf(
        "gonorrea", "gonorreas", "hijueputa", "hijueputas", "hp", "hdp", "malparido", "malparida",
        "malparidos", "malparidas", "carechimba", "carechimbas", "caremonda", "caremondas",
        "marica", "maricas", "maricon", "maricones", "mariconada", "pirobo", "pirobos", "piroba",
        "pirobas", "mierda", "mierdas", "puta", "putas", "puto", "putos", "putiad", "culiao",
        "culiada", "culiado", "culia", "zorra", "zorras", "perra", "perras", "imbecil", "imbeciles",
        "estupido", "estupida", "estupidos", "estupidas", "pendejo", "pendeja", "pendejos", "pendejas",
        "verga", "vergas", "chupame", "chupala", "carepicha", "chucha", "guevon", "guevones",
        "huevon", "huevones", "mamaguevo", "mamaguevos", "sapo", "sapos", "maldito", "maldita",
        "baboso", "babosa", "bastardo", "bastarda", "perro", "perros", "coño", "coños"
    )

    private val REGEX_PATTERN: Pattern by lazy {
        val patronStr = PALABRAS_OFENSIVAS.joinToString("|") { "\\b(?i)${Regex.escape(it)}\\b|(?i)${Regex.escape(it)}" }
        Pattern.compile(patronStr)
    }

    /**
     * Limpia el texto reemplazando cualquier término inapropiado por '****'.
     */
    fun censurar(texto: String?): String {
        if (texto.isNullOrBlank()) return ""
        val matcher = REGEX_PATTERN.matcher(texto)
        return matcher.replaceAll("****")
    }

    /**
     * Retorna true si el texto contiene alguna palabra inapropiada.
     */
    fun contieneGroseria(texto: String?): Boolean {
        if (texto.isNullOrBlank()) return false
        return REGEX_PATTERN.matcher(texto).find()
    }
}
