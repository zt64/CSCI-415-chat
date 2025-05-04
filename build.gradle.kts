plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

application {
    mainClass.set("Main")
}