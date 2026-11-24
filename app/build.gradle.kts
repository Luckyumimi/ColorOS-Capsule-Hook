plugins {
    id("com.android.application") version "9.3.3"
}

android {
    namespace = "io.github.luckyumimi.capsulehook"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "io.github.luckyumimi.capsulehook"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}
