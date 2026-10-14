plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.railtap"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.railtap"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.1"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.1.1")

    // Lets the Supabase libraries use newer Java time classes on Android 7 (your minSdk is 24)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}