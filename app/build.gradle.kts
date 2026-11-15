plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.sistemasuper.chess"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.sistemasuper.chess"
        minSdk = 29
        targetSdk = 34
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "1.$build"
        ndk { abiFilters += "arm64-v8a" }
    }

    // Chave fixa de uso pessoal: novas versoes atualizam por cima da anterior.
    signingConfigs {
        create("chess") {
            storeFile = file("chess.keystore")
            storePassword = "chessauto"
            keyAlias = "chess"
            keyPassword = "chessauto"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("chess")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // O Stockfish vai dentro do APK como libstockfish.so e e executado a partir da
    // pasta de bibliotecas nativas do app; por isso precisa ser extraido na instalacao.
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstockfish.so"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
