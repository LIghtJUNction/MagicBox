plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = providers.gradleProperty("project.namespace.base").get()
    compileSdk = providers.gradleProperty("android.compileSdk").map(String::toInt).get()
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = providers.gradleProperty("project.namespace.base").get()
        minSdk = providers.gradleProperty("android.minSdk").map(String::toInt).get()
        targetSdk = providers.gradleProperty("android.targetSdk").map(String::toInt).get()
        versionCode = providers.gradleProperty("project.version.code").map(String::toInt).get()
        versionName = providers.gradleProperty("project.version.name").get()
        manifestPlaceholders["appName"] = providers.gradleProperty("project.name").get()
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(providers.gradleProperty("android.jvm").get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            kotlin.srcDir("src")
            res.srcDir("res")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
