import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.aura.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aura.player"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Media3: playback + system media session (headset / lock screen / notification)
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")
    implementation("androidx.media3:media3-datasource:1.7.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.7.1")
    implementation("com.google.guava:guava:33.4.0-android")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    implementation("io.coil-kt:coil-compose:2.7.0")
}

// ── Auto-publish pipeline ─────────────────────────────────────────────────────
// assembleRelease → copies the signed APK into server/public/ (the download
// page's store), commits just that path, and pushes. Render redeploys on push,
// so the live page picks up the new build with zero manual steps.
val apkVersion: String = android.defaultConfig.versionName ?: "dev"

tasks.register("publishApk") {
    group = "publish"
    description = "Stage the release APK on the download page and push it (Render auto-redeploys)"
    doLast {
        val repoRoot = rootProject.projectDir.parentFile
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        check(apk.exists()) { "No release APK found at $apk — did assembleRelease run?" }

        val dest = File(repoRoot, "server/public/Aura-$apkVersion.apk")
        dest.parentFile.mkdirs()
        val changed = !dest.exists() || !apk.readBytes().contentEquals(dest.readBytes())
        apk.copyTo(dest, overwrite = true)
        logger.lifecycle("Staged ${dest.name} (${apk.length() / 1024 / 1024} MB) on the download page")

        if (!changed) {
            logger.lifecycle("APK identical to published copy — nothing to push")
            return@doLast
        }

        fun git(vararg args: String): Int {
            val proc = ProcessBuilder(listOf("git", "-C", repoRoot.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            if (code != 0) logger.info("git ${args.joinToString(" ")} exited $code:\n$output.trim()")
            return code
        }

        git("add", "server/public")
        val staged = git("diff", "--cached", "--quiet", "--", "server/public") // 1 = something staged
        if (staged != 1) {
            logger.lifecycle("No staged APK change — nothing to push")
            return@doLast
        }
        if (git("commit", "-m", "APK: Aura-$apkVersion") != 0) {
            throw GradleException("git commit failed — fix the working tree and retry")
        }
        logger.lifecycle("Pushing Aura-$apkVersion.apk → Render will redeploy in ~1-2 min")
        if (git("push") != 0) {
            throw GradleException("git push failed — check credentials/remote")
        }
        logger.lifecycle("Done. Page updates at https://aura-server-weqe.onrender.com once the deploy finishes")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("publishApk")
}
