plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

val ktorVersion = "2.3.12"

android {
    namespace = "it.marcolipparini.sfide.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.marcolipparini.sfide"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/INDEX.LIST",
            "META-INF/DEPENDENCIES",
            "META-INF/io.netty.versions.properties",
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/*.kotlin_module",
        )
    }

    // I client web (viewer/spectator) vengono impacchettati come asset (web/...).
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/webAssets"))
}

val copyWeb by tasks.registering(Sync::class) {
    from(rootProject.file("../web")) {
        include("viewer/**", "spectator/**")
    }
    into(layout.buildDirectory.dir("generated/webAssets/web"))
}

tasks.named("preBuild") { dependsOn(copyWeb) }

dependencies {
    implementation(project(":engine"))
    implementation(project(":persistence"))
    implementation(project(":server")) {
        // Su Android usiamo il logging di sistema: niente logback (evita conflitti).
        exclude(group = "ch.qos.logback")
    }

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.nayuki:qrcodegen:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
}
