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
            "direccion_aprox" to direccionAprox,
            "nivelRiesgo" to nivelRiesgo,
            "nivel_riesgo" to nivelRiesgo,
            "categoria" to categoria,
            "justificacion" to justificacion,
            "horarioCritico" to horarioCritico,
            "horario_critico" to horarioCritico,
            "timestamp" to timestamp,
            "usuarioAlias" to usuarioAlias,
            "usuario_alias" to usuarioAlias,
            "votosApoyo" to votosApoyo,
            "votos_apoyo" to votosApoyo
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ReporteCuadra {
            return ReporteCuadra(
                id = id,
                latitud = (map["latitud"] as? Number)?.toDouble() ?: 0.0,
                longitud = (map["longitud"] as? Number)?.toDouble() ?: 0.0,
                direccionAprox = (map["direccionAprox"] ?: map["direccion_aprox"]) as? String ?: "",
                nivelRiesgo = (map["nivelRiesgo"] ?: map["nivel_riesgo"] ?: map["peligro"]) as? String ?: "Medio",
                categoria = map["categoria"] as? String ?: "Robo frecuente",
                justificacion = map["justificacion"] as? String ?: "",
                horarioCritico = (map["horarioCritico"] ?: map["horario_critico"]) as? String ?: "Noche",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                usuarioAlias = (map["usuarioAlias"] ?: map["usuario_alias"]) as? String ?: "Vecino anónimo",
                votosApoyo = ((map["votosApoyo"] ?: map["votos_apoyo"]) as? Number)?.toInt() ?: 0
            )
        }
    }
}
