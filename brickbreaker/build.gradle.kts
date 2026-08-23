plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.bee.brickbreaker"
    compileSdk  = 36

    defaultConfig {
        minSdk         = 26
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
