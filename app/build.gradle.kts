plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.poltys.zb_sniffer"
    compileSdk = 34

    signingConfigs {
        // vars from GRADLE_HOME_DIR (default /home/user/.gradle) gradle.properties file
        val signingStoreFile: String? by project
        val signingStorePassword: String? by project
        val signingKeyAlias: String? by project
        val signingKeyPassword: String? by project

        this.create("Release") {
            storeFile = signingStoreFile?.let { file(it) }
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }
    defaultConfig {
        applicationId = "com.poltys.zb_sniffer"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val serverUrl: String? by project
        if (serverUrl != null) {
            resValue("string", "server_url", serverUrl!!)
        } else {
            resValue("string", "server_url", "http://10.0.2.2:50051/")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("Release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    val javaVersion = JavaVersion.valueOf("VERSION_${libs.versions.javaVersion.getOrElse("17")}")
//    project.logger.lifecycle("Java: $javaVersion")
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.kotlinCompiler.getOrElse("1.5.14")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    api(libs.kotlinx.coroutines.core)
    api(libs.grpc.kotlin.stub)
    api(libs.grpc.protobuf.lite)
    api(libs.protobuf.kotlin.lite)

    implementation(project(":stub-protos"))
    runtimeOnly(libs.grpc.okhttp)
}