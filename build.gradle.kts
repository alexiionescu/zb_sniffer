// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.protobuf) apply false
}

val protosEnabled: String? by project

task("compileProtos") {
    dependsOn(":stub-protos:compileDebugKotlin")
    doLast {
        println("Protos classes generated")
    }
}

subprojects {
    // Protos generation is disabled by default to speed up build
    // If .proto files change, run './gradlew compileProtos -PprotosEnabled=true'
    if (protosEnabled == null) {
        project.logger.lifecycle("Protos projects are disable !!!. Run manual with ./gradlew compileProtos -PprotosEnabled=true")
        tasks.whenTaskAdded(Action<Task> {
            if (this.project.name.contains("protos")) {
                this.enabled = false
                // project.logger.info("Disable TASK '${this.project.name}:${this.name}'")
            }
        })
    }
}