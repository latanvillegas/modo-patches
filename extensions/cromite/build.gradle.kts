import com.android.build.api.dsl.ApplicationExtension

// Cromite runtime extension; bundled into target APK only after a dedicated
// Morphe extension-injection patch is wired and verified.
configure<ApplicationExtension> {
    namespace = "app.morphe.extension.cromite"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
