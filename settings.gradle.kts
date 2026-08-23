pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local repo produced by `flutter build aar` in games-src/arrowescape_module
        maven { url = uri("${rootDir}/games-src/arrowescape_module/build/host/outputs/repo") }
        // Flutter engine artifacts (embedding + per-ABI aars)
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
    }
}
rootProject.name = "GamePlayer"
include(":app", ":brickbreaker", ":flowit", ":anuto")
