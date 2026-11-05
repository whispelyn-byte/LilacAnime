// PC/CI build of the app module only (the phone IDE, CodeAssist, builds from app/module.toml instead).
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "LilacAnime"
include(":app")
