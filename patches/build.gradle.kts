group = "app.morphe"

patches {
    about {
        name = "Morphe Patches"
        description = "Patches for Morphe"
        source = "git@github.com:MorpheApp/morphe-patches.git"
        author = "MorpheApp"
        contact = "na"
        website = "https://morphe.software"
        license = "GNU General Public License v3.0, with additional GPL section 7 requirements"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)

    patchListGeneratorClasspath(libs.gson)

    // Android API stubs defined here.
    compileOnly(project(":patches:stub"))
}

tasks {
    register<JavaExec>("checkStringResources") {
        description = "Checks resource strings for invalid formatting"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.patches.util.resource.CheckStringResourcesKt")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}


/**
 * Opt-in Cromite-only compilation. The default build is unchanged.
 *
 * Kotlin's source set filter is applied before compilation, so unrelated
 * patches are not compiled at all. Shared utility sources are retained.
 * Invoke with -PcromiteLite=true.
 */
if (providers.gradleProperty("cromiteLite").orNull == "true") {
    kotlin {
        sourceSets.named("main") {
            kotlin.setSrcDirs(listOf("src/main/kotlin"))
            kotlin.include("app/morphe/patches/cromite/**")
            kotlin.include("app/morphe/util/**")
            // Exclude unrelated resource validation tools, which depend on\n            // other application modules not included in Lite mode.
        }
    }
}
