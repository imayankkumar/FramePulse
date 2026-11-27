pluginManagement {
    // The FramePulse Gradle plugin, built from source for the demo app.
    includeBuild("framepulse-gradle-plugin")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FramePulse"
include(":app")
include(":framepulse")
include(":framepulse-report")
include(":benchmark")
