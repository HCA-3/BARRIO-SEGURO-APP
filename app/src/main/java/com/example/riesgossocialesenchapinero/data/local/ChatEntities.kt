package com.example.riesgossocialesenchapinero.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un mensaje del historial de chat, tal cual se manda/recibe del backend
 * (incluye mensajes de rol "tool" y "assistant" sin content, que son ruido
 * de las llamadas a herramientas — se filtran solo al mostrar en pantalla,
 * pero hay que guardarlos igual porque el backend los necesita para
 * mantener el contexto completo de la conversación en el siguiente turno).
 */
@Entity(tableName = "mensajes")
data class MensajeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Un hecho duradero que el agente aprendió del usuario (ver herramienta
 * recordar_hecho en backend_riesgo.py), guardado localmente en el celular
 * -- no hay cuenta de usuario ni backend con estado, así que la memoria
 * vive aquí. Visible y borrable por el usuario en la app.
 */
@Entity(tableName = "hechos_recordados")
data class HechoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val texto: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
