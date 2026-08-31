package com.example.riesgossocialesenchapinero.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.data.local.AppDatabase
import com.example.riesgossocialesenchapinero.data.local.ConversacionEntity
import com.example.riesgossocialesenchapinero.data.local.HechoEntity
import com.example.riesgossocialesenchapinero.data.local.MensajeEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ChatUiState(
    val historial: List<ApiClient.MensajeChat> = emptyList(),
    val hechosRecordados: List<String> = emptyList(),
    val enviando: Boolean = false,
    val error: String? = null,
    val cargandoHistorial: Boolean = true,
    val conversaciones: List<ConversacionEntity> = emptyList(),
    val conversacionActualId: Long = 0L,
    val ubicacionConcedida: Boolean = false,
)

/**
 * El historial de chat vive agrupado por conversación (Room) en vez de un
 * único hilo continuo: así la app puede mostrar un historial de chats (como
 * ChatGPT/Claude) y cambiar entre ellos. Los hechos recordados (ver
 * recordar_hecho en el backend) siguen siendo globales a propósito -- son
 * sobre el usuario, no sobre una conversación puntual, así que aplican en
 * todas. No hay cuenta de usuario/backend con estado -- todo vive en el
 * celular de cada quien.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.obtener(application)
    private val mensajeDao = db.mensajeDao()
    private val hechoDao = db.hechoDao()
    private val conversacionDao = db.conversacionDao()
    private val clienteUbicacion = LocationServices.getFusedLocationProviderClient(application)

    private val _estado = MutableStateFlow(ChatUiState())
    val estado: StateFlow<ChatUiState> = _estado

    // Solo mensajes de usuario/asistente con contenido: los de rol "tool" y
    // los de "assistant" que solo traen tool_calls (sin content) son ruido
    // de la llamada a herramientas, no algo para mostrarle al usuario.
    val mensajesVisibles: List<ApiClient.MensajeChat>
        get() = _estado.value.historial.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }

    init {
        viewModelScope.launch {
            val hechosGuardados = withContext(Dispatchers.IO) { hechoDao.obtenerTextos() }
            val conversacionInicialId = withContext(Dispatchers.IO) {
                conversacionDao.obtenerTodas().firstOrNull()?.id
                    ?: conversacionDao.insertar(ConversacionEntity())
            }
            val mensajesGuardados = withContext(Dispatchers.IO) {
                mensajeDao.obtenerPorConversacion(conversacionInicialId)
            }
            _estado.value = _estado.value.copy(
                historial = mensajesGuardados.map { it.aMensajeChat() },
                hechosRecordados = hechosGuardados,
                conversacionActualId = conversacionInicialId,
                cargandoHistorial = false,
                ubicacionConcedida = tienePermisosUbicacion(),
            )
        }
        viewModelScope.launch {
            conversacionDao.observarTodas().collectLatest { lista ->
                _estado.value = _estado.value.copy(conversaciones = lista)
            }
        }
    }

    fun enviarMensaje(texto: String) {
        if (texto.isBlank() || _estado.value.enviando) return

        val conversacionId = _estado.value.conversacionActualId
        val esPrimerMensaje = _estado.value.historial.isEmpty()
        val mensajeUsuario = ApiClient.MensajeChat("user", texto)
        val historialConPregunta = _estado.value.historial + mensajeUsuario
        _estado.value = _estado.value.copy(historial = historialConPregunta, enviando = true, error = null)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mensajeDao.insertar(
                    MensajeEntity(
                        conversacionId = conversacionId,
                        role = mensajeUsuario.role,
                        content = mensajeUsuario.content,
                    )
                )
                conversacionDao.tocar(conversacionId)
                if (esPrimerMensaje) conversacionDao.actualizarTitulo(conversacionId, tituloDesde(texto))
            }

            _estado.value = try {
                val (lat, lng) = obtenerUbicacionActual()
                val respuesta = withContext(Dispatchers.IO) {
                    ApiClient.enviarMensajeChat(
                        historialConPregunta,
                        _estado.value.hechosRecordados,
                        lat = lat,
                        lng = lng,
                    )
                }

                withContext(Dispatchers.IO) {
                    // El historial que vuelve del backend ya incluye el mensaje del
                    // usuario que mandamos (más lo nuevo: assistant/tool/etc):
                    // persistimos solo lo NUEVO para no duplicar lo ya guardado.
                    respuesta.mensajes.drop(historialConPregunta.size).forEach {
                        mensajeDao.insertar(
                            MensajeEntity(
                                conversacionId = conversacionId,
                                role = it.role,
                                content = it.content,
                                toolCallsJson = it.toolCalls,
                            )
                        )
                    }
                    respuesta.hechosNuevos.forEach { hecho ->
                        if (hechoDao.existeTexto(hecho) == 0) hechoDao.insertar(HechoEntity(texto = hecho))
                    }
                }

                _estado.value.copy(
                    historial = respuesta.mensajes,
                    hechosRecordados = _estado.value.hechosRecordados + respuesta.hechosNuevos,
                    enviando = false,
                )
            } catch (e: java.io.IOException) {
                // No se pudo conectar: ApiClient ya probó las otras direcciones
                // sin suerte, así que el arreglo está en la pestaña Riesgo.
                _estado.value.copy(
                    enviando = false,
                    error = "No hay conexión con el backend (${ApiClient.baseUrl}). " +
                        "Configúralo en la pestaña Riesgo, campo Servidor.",
                )
            } catch (e: Exception) {
                _estado.value.copy(enviando = false, error = e.message ?: "Error desconocido hablando con el agente")
            }
        }
    }

    /** Crea una conversación nueva y cambia a ella (queda vacía hasta el primer mensaje). */
    fun nuevaConversacion() {
        if (_estado.value.historial.isEmpty()) return // ya está en una conversación vacía, no crear otra igual
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { conversacionDao.insertar(ConversacionEntity()) }
            _estado.value = _estado.value.copy(conversacionActualId = id, historial = emptyList(), error = null)
        }
    }

    fun seleccionarConversacion(id: Long) {
        if (id == _estado.value.conversacionActualId) return
        viewModelScope.launch {
            val mensajes = withContext(Dispatchers.IO) { mensajeDao.obtenerPorConversacion(id) }
            _estado.value = _estado.value.copy(
                conversacionActualId = id,
                historial = mensajes.map { it.aMensajeChat() },
                error = null,
            )
        }
    }

    /** Sin id borra la conversación ACTUAL (ver botón "Borrar conversación" en el diálogo de memoria). */
    fun borrarConversacion(id: Long? = null) {
        val objetivo = id ?: _estado.value.conversacionActualId
        val esLaActual = objetivo == _estado.value.conversacionActualId
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mensajeDao.borrarPorConversacion(objetivo)
                conversacionDao.borrar(objetivo)
            }
            if (!esLaActual) return@launch

            // Si se borró la conversación que se estaba viendo, hay que
            // pararse en otra -- la más reciente que quede, o una nueva si no
            // queda ninguna.
            val siguienteId = withContext(Dispatchers.IO) {
                conversacionDao.obtenerTodas().firstOrNull()?.id ?: conversacionDao.insertar(ConversacionEntity())
            }
            val mensajesSiguiente = withContext(Dispatchers.IO) { mensajeDao.obtenerPorConversacion(siguienteId) }
            _estado.value = _estado.value.copy(
                conversacionActualId = siguienteId,
                historial = mensajesSiguiente.map { it.aMensajeChat() },
            )
        }
    }

    fun borrarHecho(texto: String) {
        _estado.value = _estado.value.copy(hechosRecordados = _estado.value.hechosRecordados - texto)
        viewModelScope.launch(Dispatchers.IO) { hechoDao.borrarPorTexto(texto) }
    }

    fun refrescarPermisos() {
        _estado.value = _estado.value.copy(ubicacionConcedida = tienePermisosUbicacion())
    }

    private fun tienePermisosUbicacion(): Boolean {
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtenerUbicacionActual(): Pair<Double?, Double?> {
        if (!tienePermisosUbicacion()) return null to null
        return try {
            val loc = clienteUbicacion.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                ?: clienteUbicacion.lastLocation.await()
            loc?.latitude to loc?.longitude
        } catch (e: Exception) {
            null to null
        }
    }
}

private fun MensajeEntity.aMensajeChat() = ApiClient.MensajeChat(role, content, toolCalls = toolCallsJson)

private fun tituloDesde(texto: String): String {
    val limpio = texto.trim().replace("\n", " ")
    return if (limpio.length <= 40) limpio else limpio.take(40) + "…"
}
