// CI only (.github/workflows/android-ci.yml): a Gradle build of the app made from app/module.toml, which the project
// itself builds with CodeAssist. The workflow copies these files into place before building.
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
    }
}

rootProject.name = "LilacAnime"
include(":app")
