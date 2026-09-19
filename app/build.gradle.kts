plugins {
    alias(libs.plugins.com.android.application)
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.com.google.dagger.hilt.android)
    alias(libs.plugins.com.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "de.langerhans.odintools"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dfdx047.odinhub"
        minSdk = 33
        targetSdk = 34
        versionCode = 11
        versionName = "0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            initWith(buildTypes.getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)

    implementation(libs.bundles.app)
    debugImplementation(libs.bundles.appDebug)
    annotationProcessor(libs.bundles.appAnnotationProcessor)
    ksp(libs.bundles.appKsp)
    testImplementation(libs.bundles.appUnitTest)
    androidTestImplementation(libs.bundles.appAndroidTest)

    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.android.compiler)
    dependencies {
        implementation("com.google.android.exoplayer:exoplayer:2.18.0")
        implementation("androidx.media3:media3-exoplayer:1.2.0")
        implementation("androidx.media3:media3-ui:1.2.0")
        implementation("androidx.core:core-ktx:1.9.0")
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    }
}
