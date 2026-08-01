import java.net.URL
import java.net.URI
import java.net.HttpURLConnection
import java.io.File
import java.io.IOException
import java.io.FileInputStream
import java.util.zip.ZipInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.azune"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  base.archivesName.set("Azune")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("downloadInterFonts") {
    doLast {
        val urlStr = "https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip"
        val fontDir = file("src/main/res/font")
        if (!fontDir.exists()) {
            fontDir.mkdirs()
        }

        println("Downloading Inter ZIP from $urlStr...")
        val tempZip = File(temporaryDir, "inter.zip")
        
        val connection = URI(urlStr).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        val code = connection.responseCode
        if (code != 200) {
            throw IOException("Server returned HTTP response code: $code for $urlStr")
        }
        
        connection.inputStream.use { input ->
            tempZip.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        println("Downloaded ZIP to ${tempZip.absolutePath} (${tempZip.length()} bytes)")
        
        // Unzip and extract fonts
        ZipInputStream(FileInputStream(tempZip)).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.endsWith(".ttf", ignoreCase = true)) {
                    val fileSimpleName = name.substringAfterLast("/")
                    val isItalic = fileSimpleName.contains("Italic", ignoreCase = true)
                    val isDisplay = fileSimpleName.contains("Display", ignoreCase = true)
                    val isStandardInter = fileSimpleName.startsWith("Inter-", ignoreCase = true)
                    
                    if (isStandardInter && !isItalic && !isDisplay) {
                        val canonicalName = when {
                            fileSimpleName.contains("Light", ignoreCase = true) -> "inter_light.ttf"
                            fileSimpleName.contains("Regular", ignoreCase = true) -> "inter_regular.ttf"
                            fileSimpleName.contains("Medium", ignoreCase = true) -> "inter_medium.ttf"
                            fileSimpleName.contains("SemiBold", ignoreCase = true) -> "inter_semibold.ttf"
                            fileSimpleName.contains("Bold", ignoreCase = true) -> "inter_bold.ttf"
                            else -> null
                        }
                        
                        if (canonicalName != null) {
                            val destFile = File(fontDir, canonicalName)
                            println("Extracting $name -> ${destFile.name}")
                            destFile.outputStream().use { output ->
                                zipInput.copyTo(output)
                            }
                        }
                    }
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
        println("Font extraction complete!")
    }
}


