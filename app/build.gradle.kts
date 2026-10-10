import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "ft.atifscodeworks.urukkumanush"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ft.atifscodeworks.urukkumanush"
        minSdk = 28
        targetSdk = 37
        versionCode = 10
        versionName = "2.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_KEYSTORE_PATH")
                ?: "C:/Users/Atif/Documents/apkKey/release.keystore"
            storeFile = file(storeFilePath)
            storePassword = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD") ?: "270508"
            keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: "main-key"
            keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: "270508"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:*.md:*.txt:*.svg:head_1.png:head_2.png:head_3.png:head_4.png:head_5.png:head_6.png:head_7.png:head_8.png:head_9.png:head_10.png:head_11.png:head_12.png"
    }

    sourceSets {
        getByName("main") {
            java.directories.add("src/main/java")
            assets.directories.add("../assets")
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}