plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gaifd.carradio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gaifd.carradio"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.3.0"
    }

    // Постоянный ключ: каждая новая сборка ставится поверх старой, станции и настройки сохраняются.
    signingConfigs {
        create("stable") {
            storeFile = rootProject.file("keystore/stable.keystore")
            storePassword = "android"
            keyAlias = "carradio"
            keyPassword = "android"
        }
    }

    // Ключ загрузки для Google Play: секретный, хранится в GitHub Secrets, в репозиторий не попадает.
    val playKeystore = System.getenv("PLAY_KEYSTORE_FILE")
    if (playKeystore != null) {
        signingConfigs.create("play") {
            storeFile = file(playKeystore)
            storePassword = System.getenv("PLAY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("PLAY_KEY_ALIAS") ?: "upload"
            keyPassword = System.getenv("PLAY_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("stable")
        }
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    // Две сборки из одного кода:
    //  github — как раньше: APK с GitHub, обновления внутри приложения;
    //  play   — для Google Play: без своей «обновлялки» и без прямого запроса снятия ограничения батареи (правила Play).
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "IN_APP_UPDATES", "true")
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "IN_APP_UPDATES", "false")
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "false")
            signingConfigs.findByName("play")?.let { signingConfig = it }
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
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
