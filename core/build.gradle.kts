plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM: the agent "brain" stays Android-free so it is fast to
// unit-test with no device. The Android app adapts AccessibilityNodeInfo into
// these types and supplies the real Perceiver/Actuator/ModelProvider.

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
