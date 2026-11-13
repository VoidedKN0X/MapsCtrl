// BRouter's routing core (util, codec, expressions, mapaccess, router), vendored unchanged from
// https://github.com/abrensch/brouter. BRouter is MIT-licensed (see LICENSE in this folder) and is not
// published on Maven Central, so it is built here from source. See README.md for the exact commit.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Upstream code; its own warnings are not ours to fix here.
    options.compilerArgs.add("-nowarn")
}
