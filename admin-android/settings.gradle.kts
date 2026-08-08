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

rootProject.name = "sfide"

// Modulo puro Kotlin/JVM: modello dati + motore voto (nessuna dipendenza Android).
include(":engine")

// Server Ktor (JVM). Gira su desktop per lo sviluppo ed è riusato embedded
// dall'app Android che fa da host.
include(":server")

// App Android host: avvia il server embedded (foreground service) + regia Compose.
include(":app")

// Persistenza locale (Room DB): template di stanza + storico partite.
include(":persistence")
