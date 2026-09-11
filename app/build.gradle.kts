import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val envClientId = System.getenv("GOOGLE_CLIENT_ID")
val googleClientId = envClientId?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty("GOOGLE_CLIENT_ID")
    ?: "149732972265-m00s0ja9vd5kg4gf3a0psnalrdu4anu1.apps.googleusercontent.com"
val redirectPrefix = googleClientId.substringBefore(".apps.googleusercontent.com")

android {
    namespace = "com.draftlock.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.draftlock.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "com.googleusercontent.apps.$redirectPrefix"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// CI helper — print runner debug SHA1 so GitHub built gives you SHA1 without touching workflow file
// (workflow's "Show debug SHA fingerprints" step does `keytool | grep` and fails if keystore missing;
//  this hook ensures keystore exists and prints SHA during assembleDebug, which IS allowed to push)
// Wrapped in afterEvaluate so assembleDebug exists (AGP creates tasks after android block)
afterEvaluate {
    tasks.named("assembleDebug") {
        doLast {
            try {
                val home = System.getProperty("user.home") ?: System.getenv("HOME") ?: "/home/runner"
                val ks = File("$home/.android/debug.keystore")
                println(">>> DraftLock: ensuring debug keystore at ${ks.absolutePath} (exists=${ks.exists()})")
                if (!ks.exists()) {
                    ks.parentFile?.mkdirs()
                    val gen = providers.exec {
                        commandLine(
                            "keytool", "-genkey", "-v",
                            "-keystore", ks.absolutePath,
                            "-storepass", "android", "-alias", "androiddebugkey", "-keypass", "android",
                            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                            "-dname", "CN=Android Debug,O=Android,C=US"
                        )
                        isIgnoreExitValue = true
                    }
                    gen.result.get()
                    println(">>> DraftLock: keytool genkey exit=${gen.result.get().exitValue} existsNow=${ks.exists()}")
                }
                try {
                    providers.exec {
                        commandLine(
                            "keytool", "-list", "-v",
                            "-keystore", ks.absolutePath,
                            "-alias", "androiddebugkey",
                            "-storepass", "android", "-keypass", "android"
                        )
                        isIgnoreExitValue = true
                    }.result.get()
                    println(">>> DraftLock: use the SHA1 above with Package com.draftlock.app to create Android OAuth client (console.cloud.google.com → Credentials → Create OAuth client → Android)")
                } catch (e: Exception) { println(">>> DraftLock: keytool list exec failed: ${e.message}") }
            } catch (e: Exception) { println(">>> DraftLock SHA helper failed: ${e.message}") }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Free asset libraries — MelonUI is web/React (ItzAmyy/MelonUI, react-melon/melon) with no Android Maven artifact,
    // so its tokens are vendored into Theme.kt (converted for Compose) rather than added as a binary dep.
    // Compatible free Compose assets (purposeful: icons + images + animation):
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.airbnb.android:lottie-compose:6.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("net.openid:appauth:0.11.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
