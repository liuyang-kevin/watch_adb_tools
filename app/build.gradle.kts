plugins {
    id("com.android.application")
}

android {
    namespace = "space.sybb.awatch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "space.sybb.awatch"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
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
    }
}
