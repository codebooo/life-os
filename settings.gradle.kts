pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "life-os"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":feature:chat")
include(":feature:capture")
include(":feature:notes")
include(":feature:reminders")
include(":feature:todo")
include(":feature:calendar")
include(":feature:messagecenter")
include(":feature:dhl")
include(":feature:imagereasoning")
include(":feature:finance")
include(":feature:email")
include(":feature:nas")
include(":feature:books")
include(":feature:route")
include(":feature:smarthome")
include(":feature:planner")
include(":core:model")
include(":core:network")
include(":core:ai")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:vault")
include(":core:service")
include(":core:ui")
