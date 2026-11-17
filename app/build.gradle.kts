import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sarim.trinity"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sarim.trinity"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.0.1"

        // ONNX Runtime ships native libraries for four ABIs. Phones use the two ARM
        // ones; dropping x86/x86_64 keeps the bundled-model APK under the 80 MB
        // size ceiling measured in the M8 spike (46.4 MB instead of 84.4 MB).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        // A real release key is optional. keystore.properties is gitignored and lives
        // only at the repo root; when it is absent every build keeps signing with the
        // debug key, so a build from source still installs and updates over itself.
        // Release-key generation and storage are documented in the private tokens repo;
        // targetSdk 35 checks are recorded in info/targetsdk-35.md in the context repo.
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = Properties().apply {
                keystorePropertiesFile.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Compose is much slower unoptimised, so the release build is the one to
            // ship: R8 shrinks the code and the resource shrinker drops unused assets.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the debug key unless keystore.properties supplies a real
            // release key, so a build from source installs and updates over itself.
            // F-Droid signs the builds it publishes with its own key.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Scheduled send: a one-time WorkManager request per pending message.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    // Installs the baseline profiles Compose ships, so a release build starts out
    // precompiled instead of interpreting its UI code until the JIT warms up.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    // M8 local embeddings: on-device ONNX Runtime for the bundled MiniLM model.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
}
