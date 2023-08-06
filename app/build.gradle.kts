plugins {
	id("com.android.application")
}

android {
	namespace = "dev.materii.rushii.xspoofsignatures"
	compileSdk = 34

	defaultConfig {
		applicationId = "dev.materii.rushii.xspoofsignatures"
		minSdk = 16
		versionCode = 1
		versionName = "1.0.0"
	}

	// signingConfigs {
	// 	named("release") {}
	// }

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro",
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-Xlint:-deprecation")
}

dependencies {
	compileOnly("de.robv.android.xposed:api:82")
}
