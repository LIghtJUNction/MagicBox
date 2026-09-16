plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

val signingValues = listOf("ANDROID_KEYSTORE_FILE", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .map { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingValues.all { !it.isNullOrBlank() }
if (signingValues.any { !it.isNullOrBlank() } && !hasReleaseSigning) error("Incomplete Android release signing configuration")

android {
    namespace = providers.gradleProperty("project.namespace.base").get()
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = providers.gradleProperty("project.namespace.base").get()
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.0-alpha.1"
        manifestPlaceholders["appName"] = "MagicBox"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("universal") {
            dimension = "distribution"
            buildConfigField("boolean", "UI_ONLY", "false")
            manifestPlaceholders["appName"] = "MagicBox"
            ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        }
        create("controller") {
            dimension = "distribution"
            applicationIdSuffix = ".ui"
            buildConfigField("boolean", "UI_ONLY", "true")
            manifestPlaceholders["appName"] = "MagicBox UI"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures { buildConfig = true; compose = true }
    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = file(signingValues[0]!!)
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    sourceSets {
        getByName("main") { manifest.srcFile("AndroidManifest.xml"); kotlin.srcDir("src"); res.srcDir("res") }
        getByName("test") { kotlin.srcDir("test") }
        getByName("universal") {
            kotlin.setSrcDirs(listOf("universal"))
            manifest.srcFile("universal/AndroidManifest.xml")
            jniLibs.setSrcDirs(listOf("runtime/jniLibs"))
            assets.setSrcDirs(listOf("runtime/assets"))
        }
        getByName("controller") { kotlin.setSrcDirs(listOf("controller")) }
        getByName("androidTest") { java.setSrcDirs(listOf("androidTest")) }
    }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/lib*.so" } }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.register("verifyBundledRuntime") {
    doLast {
        for (abi in listOf("arm64-v8a", "x86_64")) {
            for (library in listOf("libsingbox.so", "libproxylink.so", "libebpfcheck.so")) {
                val binary = file("runtime/jniLibs/$abi/$library")
                check(binary.isFile && binary.length() > 1024) { "Missing pinned runtime: $binary. Run Pinned proxy runtime first." }
                binary.inputStream().use { stream ->
                    check(stream.readNBytes(4).contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) { "Runtime is not ELF: $binary" }
                }
            }
        }
    }
}
tasks.configureEach {
    if (name == "preUniversalDebugBuild" || name == "preUniversalReleaseBuild") dependsOn("verifyBundledRuntime")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.compose.animation:animation-core")
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
