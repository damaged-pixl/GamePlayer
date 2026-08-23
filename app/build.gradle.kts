plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.bee.gameplayer"
    compileSdk  = 36

    defaultConfig {
        applicationId  = "com.bee.gameplayer"
        minSdk         = 26
        targetSdk      = 36
        versionCode    = 1
        versionName    = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(project(":brickbreaker"))
    implementation(project(":flowit"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.10.0")
}
