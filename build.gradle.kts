// Root build file. Plugins are declared here with `apply false` and applied in
// each module. Versions are centralized in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
