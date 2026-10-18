import com.android.build.api.dsl.ApplicationExtension

// The Morphe Gradle plugin packages this module as a DEX extension resource.
// No runtime hook is enabled until the patch registers extendWith.
extension {
    name = "extensions/filemanager.mpe"
}

configure<ApplicationExtension> {
    namespace = "app.morphe.extension.filemanager"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
