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
            val url = URI.create("http://127.0.0.1:8000/health").toURL()
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
        val script = scriptPath.get()
        val workDir = File(workingDirPath.get())
        ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script)
            .directory(workDir)
            .start()
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