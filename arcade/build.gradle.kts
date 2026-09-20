plugins {
    id("com.android.library")
}

android {
    namespace   = "com.bee.arcade"
    compileSdk  = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}