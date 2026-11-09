pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "ITUVideoEditor"
// Gradle automatically loads gradle/libs.versions.toml as the 'libs' catalog.
include(":app")
