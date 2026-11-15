plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.lucassilva.tv"; compileSdk = 36; defaultConfig { applicationId = "com.example.lucassilva.tv"; minSdk = 23; targetSdk = 35; versionCode = 3; versionName = "3.0-LibVLC" } 

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    

    

    kotlinOptions {
        jvmTarget = "17"
    }
}


dependencies { implementation("org.videolan.android:libvlc-all:3.7.7") }
