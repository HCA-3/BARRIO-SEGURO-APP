import java.net.URI
import java.net.HttpURLConnection
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.riesgossocialesenchapinero"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.riesgossocialesenchapinero"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

abstract class EnsureBackendTask : DefaultTask() {
    @get:Input
    abstract val scriptPath: Property<String>

    @get:Input
    abstract val workingDirPath: Property<String>

    @TaskAction
    fun run() {
        try {
            val url = URI.create("http://127.0.0.1:8001/health").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                return
            }
        } catch (_: Exception) {
            // El backend no está corriendo, iniciarlo en segundo plano
        }
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows) {
            val script = scriptPath.get()
            val workDir = File(workingDirPath.get())
            try {
                ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script)
                    .directory(workDir)
                    .start()
            } catch (_: Exception) {
            }
        }
    }
}

val rootDirFile = layout.projectDirectory.asFile.parentFile
tasks.register<EnsureBackendTask>("ensureBackendRunning") {
    scriptPath.set(rootDirFile.resolve("iniciar.ps1").absolutePath)
    workingDirPath.set(rootDirFile.absolutePath)
}

tasks.matching { it.name.startsWith("preBuild") }.configureEach {
    dependsOn("ensureBackendRunning")
}

abstract class CopyApkTask : DefaultTask() {
    @get:InputDirectory
    abstract val apkOutputDir: DirectoryProperty

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val rootDir = rootDirectory.asFile.get()
        val targetApkDir = File(rootDir, "apk")
        if (!targetApkDir.exists()) {
            targetApkDir.mkdirs()
        }

        // Guardar el control de versiones fuera de la carpeta apk para dejar solo archivos .apk
        val versionFile = File(rootDir, ".apk_version")
        var versionNumber = 0
        if (versionFile.exists()) {
            val text = versionFile.readText().trim()
            versionNumber = text.toIntOrNull() ?: 0
        }

        val buildApkDir = apkOutputDir.asFile.get()
        val apkFiles = buildApkDir.listFiles { _, name -> name.endsWith(".apk") } ?: emptyArray()

        for (srcApk in apkFiles) {
            val destVersionedApk = File(targetApkDir, "app-v${versionNumber}.apk")
            val destLatestApk = File(targetApkDir, "app-latest.apk")
            srcApk.copyTo(destVersionedApk, overwrite = true)
            srcApk.copyTo(destLatestApk, overwrite = true)
            println("==================================================")
            println("APK instalable generado en: ${destVersionedApk.absolutePath}")
            println("Copia 'latest' actualizada en: ${destLatestApk.absolutePath}")
            println("Versión de APK: v${versionNumber}")
            println("==================================================")
        }

        // Incrementar la versión para la siguiente compilación
        versionFile.writeText("${versionNumber + 1}\n")
    }
}


tasks.register<CopyApkTask>("copyApkToApkFolder") {
    rootDirectory.set(rootDirFile)
    apkOutputDir.set(layout.buildDirectory.dir("outputs/apk/debug"))
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyApkToApkFolder")
}