plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.school_restaurant_gestion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.school_restaurant_gestion"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // AndroidX Libraries
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ZXing Library for QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // ZXing Scanner
    implementation ("androidx.camera:camera-core:1.3.0")
    implementation ("androidx.camera:camera-camera2:1.1.0-beta01")
    implementation ("androidx.camera:camera-view:1.3.0")
    implementation ("androidx.camera:camera-lifecycle:1.3.0")
    implementation ("com.google.zxing:core:3.5.1")


    // Retrofit for API Calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp for HTTP Requests
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Gson for JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // RecyclerView (optional, for lists)
    implementation("androidx.recyclerview:recyclerview:1.3.1")

    // SharedPreferences for session persistence
    implementation("androidx.preference:preference:1.2.0")
    implementation("androidx.camera:camera-lifecycle:1.4.1")

    // Test Libraries
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}