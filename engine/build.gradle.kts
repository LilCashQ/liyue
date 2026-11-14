plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
    // Chunker sources are large and deeply nested; a bigger compiler stack keeps javac happy.
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("-Xss8m")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.gson)
    implementation(libs.fastutil)
    implementation(libs.lz4)
    implementation(libs.leveldb)
    implementation(libs.leveldbApi)
    compileOnly(libs.jetbrainsAnnotations)
}
