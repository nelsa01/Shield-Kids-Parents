plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
<<<<<<< HEAD
    alias(libs.plugins.google.gms.google.services)

}

android {
    namespace = "com.shieldtechhub.shieldkids"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shieldtechhub.shieldkids"
        minSdk = 24
=======
}

android {
    namespace = "com.shieldtechub.shieldkidsparents"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shieldtechub.shieldkidsparents"
        minSdk = 29
>>>>>>> c6f01aebb4ab9b3469730c25c4e2aa368a66a774
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
<<<<<<< HEAD
        viewBinding = true
=======
>>>>>>> c6f01aebb4ab9b3469730c25c4e2aa368a66a774
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
<<<<<<< HEAD
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.appcompat)
    implementation(libs.firebase.messaging)
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
=======
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
>>>>>>> c6f01aebb4ab9b3469730c25c4e2aa368a66a774
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
<<<<<<< HEAD

=======
>>>>>>> c6f01aebb4ab9b3469730c25c4e2aa368a66a774
}