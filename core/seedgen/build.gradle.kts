import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.liyue.mctoolbox.seedgen"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")

    // The native library is prebuilt into src/main/jniLibs/arm64-v8a by
    // tools/build-android-so.sh. CMakeLists.txt under src/main/cpp is still the
    // source of truth for the file list and is used for host-side tests via
    // tools/verify-seedgen.sh.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    System.getProperty("liyue.nativeLib")?.let { systemProperty("liyue.nativeLib", it) }
}
