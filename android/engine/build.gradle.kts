plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// The data files live in /docs and stay the single source of truth;
// they are packaged as classpath resources at build time.
sourceSets {
    main {
        resources {
            srcDir("../../docs")
            include("exercise_library.json", "plan_tables.json")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
