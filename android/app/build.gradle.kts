plugins {
    id("com.android.application")
}

android {
    namespace = "com.sagisawa.gbfaccelerator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sagisawa.gbfaccelerator"
        minSdk = 26
        targetSdk = 35
        versionCode = 120
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(layout.buildDirectory.dir("generated/licenseAssets").get().asFile.absolutePath)
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation(files("libs/cmfa-core-release.aar"))
    implementation(files("libs/cmfa-common-release.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

val buildGoCore = tasks.register<Exec>("buildGoCore") {
    val coreDir = rootProject.projectDir.parentFile.resolve("core")
    workingDir = coreDir
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")
    commandLine("go", "build", "-trimpath", "-ldflags=-s -w", "-o", file("src/main/jniLibs/arm64-v8a/libgbfcore.so").absolutePath, ".")
}

val copyLicenseAssets = tasks.register<Copy>("copyLicenseAssets") {
    val repoRoot = rootProject.projectDir.parentFile
    from(repoRoot.resolve("licenses")) {
        into("licenses")
    }
    from(repoRoot.resolve("NOTICE"))
    into(layout.buildDirectory.dir("generated/licenseAssets"))
}

tasks.named("preBuild") {
    dependsOn(buildGoCore, copyLicenseAssets)
}
