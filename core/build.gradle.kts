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

    // Surface credentials to the gated live smoke test (ClaudeLiveSmokeTest).
    // Absent -> the test self-skips via Assume, so the default run stays offline
    // and free. Present -> it does one real call on the cheapest model.
    listOf("ANTHROPIC_API_KEY", "ANTHROPIC_TEST_MODEL").forEach { name ->
        System.getenv(name)?.let { environment(name, it) }
    }
    // Never serve a cached result for the live path: a newly-exported key must
    // actually trigger the call. The unit suite is tiny, so re-running is cheap.
    outputs.upToDateWhen { false }
}
