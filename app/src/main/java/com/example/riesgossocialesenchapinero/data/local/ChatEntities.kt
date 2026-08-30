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
    // A qué conversación pertenece (ver ConversacionEntity). No se declara
    // como @ForeignKey formal para no forzar un orden de borrado estricto --
    // MensajeDao.borrarPorConversacion ya se encarga de limpiar los mensajes
    // antes de borrar la conversación.
    val conversacionId: Long,
    val role: String,
    val content: String,
    // JSON crudo del array "tool_calls" de Ollama (ver ApiClient.MensajeChat):
    // null si este mensaje no llamó ninguna herramienta. Sin esto, un mensaje
    // "assistant" que llamó una herramienta se guardaba como si no hubiera
    // pasado nada, dejando el "tool" que le sigue sin el turno que lo
    // originó -- el modelo perdía el hilo en la siguiente pregunta de
    // seguimiento porque el historial que se le reenviaba ya no tenía
    // sentido para su formato de tool-calling.
    val toolCallsJson: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Una conversación de chat independiente (ver ChatViewModel): el historial
 * de mensajes ya no es un único hilo continuo, sino que se agrupa por
 * conversación para poder mostrar un historial de chats en la app (como en
 * ChatGPT/Claude) y cambiar entre ellas.
 */
@Entity(tableName = "conversaciones")
data class ConversacionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Se arranca con un título genérico y se reemplaza por un resumen del
    // primer mensaje del usuario en cuanto lo manda (ver
    // ChatViewModel.tituloDesde) -- así la lista de conversaciones es
    // identificable de un vistazo, no todas dicen lo mismo.
    val titulo: String = "Nueva conversación",
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
