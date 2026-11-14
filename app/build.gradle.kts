plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.sistemasuper.yoru"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.sistemasuper.yoru"
        minSdk = 28
        targetSdk = 34
        // Cada build no GitHub sobe o numero, entao atualiza por cima da versao anterior.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "1.$build"
    }

    // Chave fixa para que novas versoes atualizem por cima da anterior,
    // sem precisar desinstalar. E uma chave de uso pessoal (sideload).
    signingConfigs {
        create("yoru") {
            storeFile = file("yoru.keystore")
            storePassword = "yorulauncher"
            keyAlias = "yoru"
            keyPassword = "yorulauncher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("yoru")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
