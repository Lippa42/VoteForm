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

// Relay WebSocket (JVM) per spettatori da remoto: inoltra i messaggi tra host e
// client senza contenere logica di gioco. Da deployare su un servizio economico.
include(":relay")

// App Admin per desktop (Compose for Desktop): stesse funzioni dell'app Android
// (builder + regia + host), pensata per mouse e tastiera.
include(":desktop")
