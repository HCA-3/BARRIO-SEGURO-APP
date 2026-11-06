package com.example.riesgossocialesenchapinero.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.riesgossocialesenchapinero.MainActivity
import com.example.riesgossocialesenchapinero.data.AjustesManager
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que revisa la ubicación del usuario cada cierto
 * intervalo y dispara una notificación si entra a una localidad con
 * nivel_riesgo "alto". Corre en segundo plano (app cerrada o celular
 * bloqueado) mientras esté activo — por eso necesita mostrar una
 * notificación persistente (obligatorio en Android para foreground services).
 */
class MonitoreoUbicacionService : Service() {

    companion object {
        const val ACTION_INICIAR = "com.example.riesgossocialesenchapinero.INICIAR_MONITOREO"
        const val ACTION_DETENER = "com.example.riesgossocialesenchapinero.DETENER_MONITOREO"

        /**
         * Si el servicio está corriendo ahora mismo. La UI lo consulta para
         * pintar el botón: antes guardaba ese estado en un `remember` de la
         * pantalla, que se pierde al cambiar de pestaña y hacía que el botón
         * dijera "Activar" con el monitoreo ya encendido.
         */
        var activo: Boolean = false
            private set

        private const val TAG = "MonitoreoUbicacion"
        private const val CANAL_MONITOREO = "monitoreo_ubicacion"
        private const val CANAL_ALERTA = "alerta_riesgo"
        private const val CANAL_ALERTA_UPZ = "alerta_llamadas_upz"
        private const val CANAL_SECTOR = "cambio_sector"
        private const val ID_NOTIFICACION_MONITOREO = 1
        private const val ID_NOTIFICACION_ALERTA = 2
        private const val ID_NOTIFICACION_ALERTA_UPZ = 3
        private const val ID_NOTIFICACION_SECTOR = 4

        // Cada cuánto se revisa la ubicación. 2 minutos: suficientemente
        // frecuente para una alerta útil ("acabo de entrar a una zona roja"),
        // sin drenar la batería como lo haría un chequeo cada pocos segundos.
        private const val INTERVALO_MS = 2 * 60 * 1000L
    }

    private lateinit var clienteUbicacion: com.google.android.gms.location.FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Última localidad y nivel de riesgo confirmados para no repetir la alerta
    // mientras el usuario siga en el mismo sector, pero sí avisar cuando cambie de localidad.
    private var ultimaLocalidad: String? = null
    private var ultimoNivelRiesgo: String? = null

    // Igual que ultimaLocalidad, pero para la capa secundaria de UPZ
    // (llamadas de emergencia). Es independiente: se puede estar en una
    // localidad de riesgo medio pero en una UPZ puntual con muchas llamadas.
    private var ultimaUpzAlertada: String? = null

    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        clienteUbicacion = LocationServices.getFusedLocationProviderClient(this)
        crearCanalesNotificacion()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(resultado: LocationResult) {
                val ubicacion = resultado.lastLocation ?: return
                Log.d(TAG, "onLocationResult: ${ubicacion.latitude}, ${ubicacion.longitude}")
                revisarRiesgo(ubicacion.latitude, ubicacion.longitude)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DETENER -> {
                detenerMonitoreo()
                return START_NOT_STICKY
            }
            else -> iniciarMonitoreo()
        }
        return START_STICKY
    }

    private fun iniciarMonitoreo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ID_NOTIFICACION_MONITOREO,
                notificacionMonitoreo(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(ID_NOTIFICACION_MONITOREO, notificacionMonitoreo())
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        activo = true

        // PRIORITY_HIGH_ACCURACY (GPS) en vez de balanced/network: en zonas
        // urbanas densas (justo el caso de uso de esta app) la ubicación por
        // red puede ser demasiado imprecisa para saber en qué localidad
        // exacta está alguien cerca de un límite.
        val ajustes = AjustesManager(this)
        val intervaloMs = (ajustes.intervaloMinutos.coerceAtLeast(1)) * 60 * 1000L
        val solicitud = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervaloMs)
            .setMinUpdateIntervalMillis(intervaloMs)
            .build()
        clienteUbicacion.requestLocationUpdates(solicitud, locationCallback, mainLooper)
            .addOnSuccessListener { Log.d(TAG, "requestLocationUpdates registrado con intervalo ${intervaloMs}ms") }
            .addOnFailureListener { e -> Log.e(TAG, "requestLocationUpdates falló", e) }

        // Chequeo inmediato al activar el monitoreo, en vez de esperar el
        // primer intervalo completo para la primera señal.
        clienteUbicacion.lastLocation
            .addOnSuccessListener { ubicacion ->
                Log.d(TAG, "lastLocation al iniciar: $ubicacion")
                if (ubicacion != null) revisarRiesgo(ubicacion.latitude, ubicacion.longitude)
            }
            .addOnFailureListener { e -> Log.e(TAG, "lastLocation falló", e) }
    }

    private fun detenerMonitoreo() {
        activo = false
        clienteUbicacion.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun revisarRiesgo(lat: Double, lng: Double) {
        scope.launch {
            val alertasHabilitadas = AjustesManager(this@MonitoreoUbicacionService).alertasHabilitadas
            if (!alertasHabilitadas) {
                Log.d(TAG, "Alertas desactivadas por el usuario en Ajustes (RF-003/RN-02)")
                return@launch
            }

            val resultado = try {
                ApiClient.consultarRiesgoPorPunto(lat, lng)
            } catch (e: Exception) {
                Log.e(TAG, "consultarRiesgoPorPunto falló (se reintenta en el próximo chequeo)", e)
                null
            }
            Log.d(TAG, "resultado riesgo para ($lat, $lng): $resultado")

            if (resultado != null) {
                val nuevaLoc = resultado.localidad
                val nuevoRiesgo = resultado.nivelRiesgo.lowercase()
                val riesgoFmt = nuevoRiesgo.replaceFirstChar { it.uppercase() }

                // Si cambió de sector/localidad o es la primera detección
                if (nuevaLoc != ultimaLocalidad) {
                    val localidadAnterior = ultimaLocalidad
                    val riesgoAnterior = ultimoNivelRiesgo

                    if (localidadAnterior != null) {
                        // Cruce de frontera entre localidades
                        if (nuevoRiesgo == "alto") {
                            mostrarNotificacionSector(
                                titulo = "⚠️ Zona de riesgo alto: $nuevaLoc",
                                mensaje = "Saliste de $localidadAnterior y entraste a $nuevaLoc (Nivel de riesgo ALTO). Mantente alerta a tus pertenencias.",
                                esAlertaAlta = true,
                            )
                        } else if (riesgoAnterior == "alto") {
                            mostrarNotificacionSector(
                                titulo = "✅ Saliste de zona de riesgo alto",
                                mensaje = "Dejaste atrás $localidadAnterior. Ahora te encuentras en $nuevaLoc (Riesgo $riesgoFmt).",
                                esAlertaAlta = false,
                            )
                        } else {
                            mostrarNotificacionSector(
                                titulo = "📍 Cambio de sector: $nuevaLoc",
                                mensaje = "Saliste de $localidadAnterior y entraste a $nuevaLoc (Riesgo $riesgoFmt).",
                                esAlertaAlta = false,
                            )
                        }
                    } else {
                        // Primera detección al activar el monitoreo
                        if (nuevoRiesgo == "alto") {
                            mostrarNotificacionSector(
                                titulo = "⚠️ Zona de riesgo alto: $nuevaLoc",
                                mensaje = "Te encuentras en $nuevaLoc, una localidad con nivel de riesgo alto.",
                                esAlertaAlta = true,
                            )
                        } else {
                            mostrarNotificacionSector(
                                titulo = "📍 Monitoreo activo en $nuevaLoc",
                                mensaje = "Te encuentras en la localidad de $nuevaLoc (Riesgo $riesgoFmt).",
                                esAlertaAlta = false,
                            )
                        }
                    }

                    ultimaLocalidad = nuevaLoc
                    ultimoNivelRiesgo = nuevoRiesgo
                }

                // Alerta específica para UPZ con alto volumen de llamadas
                val upz = resultado.upz
                if (upz?.nivelLlamadas == "alto") {
                    if (upz.upz != ultimaUpzAlertada) {
                        ultimaUpzAlertada = upz.upz
                        mostrarAlertaUpz(upz.upz)
                    }
                } else {
                    ultimaUpzAlertada = null
                }
            } else {
                // resultado == null: El usuario salió del perímetro de Bogotá
                if (ultimaLocalidad != null) {
                    val salioDe = ultimaLocalidad
                    mostrarNotificacionSector(
                        titulo = "ℹ️ Fuera de Bogotá",
                        mensaje = "Saliste de $salioDe y estás fuera del perímetro de Bogotá. Monitoreo de riesgo en espera.",
                        esAlertaAlta = false,
                    )
                    ultimaLocalidad = null
                    ultimoNivelRiesgo = null
                    ultimaUpzAlertada = null
                }
            }
        }
    }

    private fun crearCanalesNotificacion() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CANAL_MONITOREO,
                "Monitoreo de ubicación",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Indica que Barrio Seguro está revisando tu ubicación en segundo plano." },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CANAL_ALERTA,
                "Alertas de zona de riesgo",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Avisa cuando entras a una localidad con riesgo alto (delito verificado)." },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CANAL_ALERTA_UPZ,
                "Alertas de llamadas de emergencia",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Avisa cuando entras a una zona más pequeña (UPZ) con muchas llamadas de " +
                    "emergencia recientes. Señal secundaria, no es delito verificado."
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CANAL_SECTOR,
                "Notificaciones de sector y localidad",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Avisa cuando cambias de localidad, entras a un nuevo sector o sales de Bogotá."
            },
        )
    }

    private fun notificacionMonitoreo(): Notification {
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CANAL_MONITOREO)
            .setContentTitle("Barrio Seguro")
            .setContentText("Monitoreando tu ubicación para avisarte de zonas de riesgo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .build()
    }

    private fun mostrarNotificacionSector(titulo: String, mensaje: String, esAlertaAlta: Boolean = false) {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val canal = if (esAlertaAlta) CANAL_ALERTA else CANAL_SECTOR
        val prioridad = if (esAlertaAlta) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        val icono = if (esAlertaAlta) android.R.drawable.ic_dialog_alert else android.R.drawable.ic_dialog_info

        val notificacion = NotificationCompat.Builder(this, canal)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setSmallIcon(icono)
            .setPriority(prioridad)
            .setAutoCancel(true)
            .setContentIntent(abrirApp)
            .build()

        val notifId = if (esAlertaAlta) ID_NOTIFICACION_ALERTA else ID_NOTIFICACION_SECTOR
        getSystemService(NotificationManager::class.java).notify(notifId, notificacion)
    }

    private fun mostrarAlertaUpz(upz: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Texto deliberadamente distinto al de la alerta oficial ("Zona de
        // riesgo alto"): esta es una señal más ruidosa (llamadas de
        // emergencia, no delito verificado) y no debe sonar igual de certera.
        val notificacion = NotificationCompat.Builder(this, CANAL_ALERTA_UPZ)
            .setContentTitle("Zona con muchas llamadas de emergencia")
            .setContentText("$upz ha tenido varias llamadas de emergencia recientes (no es delito confirmado).")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(abrirApp)
            .build()

        getSystemService(NotificationManager::class.java).notify(ID_NOTIFICACION_ALERTA_UPZ, notificacion)
    }

    override fun onDestroy() {
        activo = false
        clienteUbicacion.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
