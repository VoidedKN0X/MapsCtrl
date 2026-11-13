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
rootProject.name = "MapsCtrl"
include(":app")
// BRouter's routing core, vendored from source (it isn't published on Maven Central). See brouter/README.md.
include(":brouter")
