plugins {
	id("com.android.application")
}

android {
	namespace = "dev.materii.rushii.xspoofsig"
	compileSdk = 34

	defaultConfig {
		applicationId = "dev.materii.rushii.xspoofsig"
		minSdk = 16
		versionCode = 1
		versionName = "1.0.0"
	}

	// signingConfigs {
	// 	named("release") {}
	// }

	packagingOptions {
		resources {
			excludes += "META-INF/**"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			// proguardFile("proguard-rules.pro")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
}

dependencies {
	compileOnly("de.robv.android.xposed:api:82")
}
