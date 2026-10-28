import java.util.Properties

// Aurora Home: an Android TV home screen. One module, kept in the original layout
// (src/, res/, assets/, AndroidManifest.xml at the top) so history stays readable.
// Kotlin and Java live side by side in src/; the games stay in Java.
plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.2.21"
}

// Your own signing key stays out of the source: keystore.properties (not in git) says where it
// is and its passwords. Without it, builds use Android's usual debug key.
val signing = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "home.aurora"
    compileSdk = 34

    defaultConfig {
        applicationId = "home.aurora"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "4.0"
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src")
        res.srcDirs("res")
        assets.srcDirs("assets")
    }
    sourceSets["test"].java.srcDirs("tests")

    // The TV only accepts updates signed with the same key: keep your keystore backed up.
    signingConfigs {
        if (signing != null) create("aurora") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    val key = if (signing != null) signingConfigs.getByName("aurora") else signingConfigs.getByName("debug")
    buildTypes {
        release {
            // Shrink and optimize with R8 (see proguard-rules.pro for what must be kept).
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = key
        }
        debug {
            // Shrunk the same way, so the emulator preview tests exactly what the TV runs.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Same key, so the emulator's debuggable preview and the TV build replace each other.
            signingConfig = key
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // A side-loaded home screen: Play Store target-SDK and permission rules don't apply.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
