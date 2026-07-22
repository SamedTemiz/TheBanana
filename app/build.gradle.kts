plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.timrashard.banana"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.timrashard.banana"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Google-provided test IDs. Never request live ads during development.
            manifestPlaceholders["adMobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField(
                "String",
                "ADMOB_REWARDED_AD_UNIT_ID",
                "\"ca-app-pub-3940256099942544/5224354917\""
            )
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["adMobAppId"] = "ca-app-pub-1547168097704291~1049495381"
            buildConfigField(
                "String",
                "ADMOB_REWARDED_AD_UNIT_ID",
                "\"ca-app-pub-1547168097704291/6451217060\""
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.retrofit2)
    implementation(libs.retrofit2.converter.gson)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.glide)
    implementation(libs.google.mobile.ads)
    implementation(libs.google.user.messaging.platform)
}
