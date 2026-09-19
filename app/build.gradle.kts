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
    // 1. Blinda o Compose forçando a BOM estável (ignora versões alfa do libs)
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)

    // 2. Ui do Compose deve vir abaixo da BOM
    implementation(libs.androidx.compose.ui)

    // 3. Suas dependências do catálogo
    implementation(libs.bundles.app)
    debugImplementation(libs.bundles.appDebug)
    annotationProcessor(libs.bundles.appAnnotationProcessor)
    ksp(libs.bundles.appKsp)
    testImplementation(libs.bundles.appUnitTest)
    androidTestImplementation(libs.bundles.appAndroidTest)

    // 4. Hilt
    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.android.compiler)

    // 5. Novo motor de vídeo (ExoPlayer moderno)
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    // 6. Força versões estáveis do Android Core para evitar exigência do SDK 35/37
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
}
