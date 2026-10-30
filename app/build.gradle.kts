import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

// CI 用 GitHub Actions 的运行序号作为版本号；本地编译默认为 1
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

// 签名：CI 从环境变量读取，本地从 keystore.properties 读取
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(envKey: String, propKey: String): String? =
    System.getenv(envKey) ?: keystoreProps.getProperty(propKey)

val releaseStoreFile = signingValue("KEYSTORE_FILE", "storeFile")

// 离线语音识别库（只有安卓用），由 tools/fetch_sherpa.sh 下载（太大，不放进仓库）
val sherpaAar = file("libs/sherpa-onnx-1.13.8.aar")

// 只编 iOS 时（macOS 上的 CI）不需要安卓的语音识别库
val buildingAndroid = gradle.startParameter.taskNames.none { it.contains("ios", ignoreCase = true) || it.contains("Framework") }
if (buildingAndroid) check(sherpaAar.exists()) { "缺少语音识别库，请先运行 tools/fetch_sherpa.sh" }

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BojiLaiKit"
            isStatic = true
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.concurrent.atomics.ExperimentalAtomicApi",
            "kotlin.time.ExperimentalTime",
            "org.jetbrains.compose.resources.ExperimentalResourceApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.material.icons.core)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.navigation.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences.core)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.coil.compose)
            implementation(libs.haze)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(compose.preview)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.datastore.preferences)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.okhttp)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.health.connect)
            if (sherpaAar.exists()) implementation(files(sherpaAar))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.ui.test.junit4)
                implementation(libs.roborazzi)
                implementation(libs.roborazzi.compose)
            }
        }
    }
}

android {
    // App 已改名「薄肌来」，但包名和 applicationId 保持 fitlog 不变：
    // 改了的话已安装的版本无法覆盖更新，只能卸载重装（会丢数据）
    namespace = "io.github.sekirankumo.fitlog"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sekirankumo.fitlog"
        minSdk = 29
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = "0.1.$ciVersionCode"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            // 调试版也用正式密钥签名（若有），这样两者可以互相覆盖安装
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // 压缩 so 文件，安装包和每次更新的下载量都小一半
        jniLibs.useLegacyPackaging = true
        // 语音识别库只打包 64 位 ARM（几乎所有在用的安卓手机），其他机型改用系统语音识别。
        // 不能用 abiFilters：那样连其他小的原生库也只剩 64 位，32 位手机就装不上了
        jniLibs.excludes += listOf(
            "lib/armeabi-v7a/libsherpa-onnx-jni.so",
            "lib/x86/libsherpa-onnx-jni.so",
            "lib/x86/libonnxruntime.so",
            "lib/x86_64/libsherpa-onnx-jni.so",
        )
    }
}

compose.resources {
    packageOfResClass = "io.github.sekirankumo.fitlog.resources"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
