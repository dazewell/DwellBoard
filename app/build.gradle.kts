import com.android.build.api.variant.ApplicationVariant
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("plugin.serialization") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
}

// DwellBoard: release signing. app/release.keystore is committed; its passwords come
// from the LOCAL_PROPERTIES env var (base64 of a properties file, set in CI from the
// secret of the same name) or the gitignored local.properties, then plain env vars.
val dwellSigning: Properties = Properties().apply {
    val b64 = System.getenv("LOCAL_PROPERTIES")
    val local = rootProject.file("local.properties")
    if (!b64.isNullOrBlank()) load(Base64.getDecoder().decode(b64.trim()).inputStream())
    else if (local.exists()) local.inputStream().use { load(it) }
}
fun dwellSigningValue(key: String): String? = dwellSigning.getProperty(key) ?: System.getenv(key)

android {
    compileSdk = 37

    // DwellBoard: only configured when the passwords are available, so builds without them still work.
    val dwellKeystorePass = dwellSigningValue("KEYSTORE_PASS")
    val dwellReleaseSigning = if (dwellKeystorePass.isNullOrBlank()) null else signingConfigs.create("release") {
        storeFile = file("release.keystore")
        storePassword = dwellKeystorePass
        keyAlias = dwellSigningValue("ALIAS_NAME")
        keyPassword = dwellSigningValue("ALIAS_PASS")
    }

    defaultConfig {
        applicationId = "helium314.keyboard.dazewell"
        minSdk = 21
        targetSdk = 37
        versionCode = 4200
        versionName = "4.2-beta1"
        // DwellBoard: staging.yml sets DWELL_BUILD_SHA so each CI build shows which commit it is (About screen, app info). Unset locally.
        versionNameSuffix = System.getenv("DWELL_BUILD_SHA")?.takeIf { it.isNotBlank() }?.let { "+$it" } ?: ""
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = dwellReleaseSigning // DwellBoard
        }
        create("nouserlib") { // same as release, but does not allow the user to provide a library
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
        }
        debug {
            // "normal" debug has minify for smaller APK to fit the GitHub 25 MB limit when zipped
            // and for better performance in case users want to install a debug APK
            isMinifyEnabled = true
            isJniDebuggable = false
            applicationIdSuffix = ".debug"
        }
        create("runTests") { // build variant for running tests on CI that skips tests known to fail
            isMinifyEnabled = false
            isJniDebuggable = false
        }
        create("debugNoMinify") { // for faster builds in IDE
            isDebuggable = true
            isMinifyEnabled = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            isDefault = true
        }

        androidComponents.onVariants { variant: ApplicationVariant ->
            if (variant.buildType == "debug") {
                // got a little too big for GitHub after some dependency upgrades, so we remove the largest dictionary
                variant.androidResources.ignoreAssetsPatterns = listOf("main_ro.dict")
                variant.proguardFiles = emptyList()
                //noinspection ProguardAndroidTxtUsage we intentionally use the "normal" file here
                variant.proguardFiles.add(project.layout.buildDirectory.file(project.buildFile.parent + "/dontoptimize.pro"))
                variant.proguardFiles.add(project.layout.buildDirectory.file(project.buildFile.parent + "/proguard-rules.pro"))
            }
            variant.outputs.forEach { output ->
                if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                    output.outputFileName = "DwellBoard_${defaultConfig.versionName}-${variant.buildType}.apk"
                }
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }
    ndkVersion = "28.0.13004108"

    packaging {
        jniLibs {
            // shrinks APK by 3 MB, zipped size unchanged
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // DwellBoard: AGP 9 only creates unit-test tasks for testBuildType; CI passes -PdwellTestBuildType=runTests
    testBuildType = providers.gradleProperty("dwellTestBuildType").getOrElse("debug")
    // DwellBoard: runTests skips XLinkTest, which HEAD-requests live upstream links and fails on link rot
    if (testBuildType == "runTests") {
        testOptions.unitTests.all { it.filter.excludeTestsMatching("helium314.keyboard.XLinkTest") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // see https://github.com/HeliBorg/HeliBoard/issues/477
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    namespace = "helium314.keyboard.latin"
    lint {
        abortOnError = true
    }
}

dependencies {
    // androidx
    implementation("androidx.core:core-ktx:1.17.0") // 1.18.0 requires minSdk 23
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.11.01")) // newer requires minSdk 23
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    "debugNoMinifyImplementation"("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("sh.calvin.reorderable:reorderable:3.1.0") // for easier re-ordering
    implementation("com.github.skydoves:colorpicker-compose:1.1.3") // for user-defined colors, newer requires minSdk 23

    // test
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("androidx.test:core:1.7.0")
}
