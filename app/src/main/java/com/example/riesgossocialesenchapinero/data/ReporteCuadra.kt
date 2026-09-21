package com.example.riesgossocialesenchapinero.data

data class ReporteCuadra(
    val id: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    val direccionAprox: String = "",
    val nivelRiesgo: String = "Medio", // "Bajo", "Medio", "Alto", "Critico"
    val categoria: String = "Robo frecuente",
    val justificacion: String = "",
    val horarioCritico: String = "Noche", // "Dia", "Noche", "Madrugada", "Todo el dia"
    val timestamp: Long = System.currentTimeMillis(),
    val usuarioAlias: String = "Vecino anónimo",
    val votosApoyo: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "latitud" to latitud,
            "longitud" to longitud,
            "direccionAprox" to direccionAprox,
            "nivelRiesgo" to nivelRiesgo,
            "categoria" to categoria,
            "justificacion" to justificacion,
            "horarioCritico" to horarioCritico,
            "timestamp" to timestamp,
            "usuarioAlias" to usuarioAlias,
            "votosApoyo" to votosApoyo
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ReporteCuadra {
            return ReporteCuadra(
                id = id,
                latitud = (map["latitud"] as? Number)?.toDouble() ?: 0.0,
                longitud = (map["longitud"] as? Number)?.toDouble() ?: 0.0,
                direccionAprox = map["direccionAprox"] as? String ?: "",
                nivelRiesgo = map["nivelRiesgo"] as? String ?: "Medio",
                categoria = map["categoria"] as? String ?: "Robo frecuente",
                justificacion = map["justificacion"] as? String ?: "",
                horarioCritico = map["horarioCritico"] as? String ?: "Noche",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                usuarioAlias = map["usuarioAlias"] as? String ?: "Vecino anónimo",
                votosApoyo = (map["votosApoyo"] as? Number)?.toInt() ?: 0
            )
        }
    }
}
