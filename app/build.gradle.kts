plugins {
	id("com.android.application")
}

android {
	namespace = "dev.rushii.xspoofsignatures"
	compileSdk = 34

	defaultConfig {
		applicationId = "dev.rushii.xspoofsignatures"
		minSdk = 3 // Yes, this is accurate
		targetSdk = 35
		versionCode = 1
		versionName = "1.0.0"
	}

	signingConfigs {
		val keystoreFile = System.getenv("KEYSTORE_FILE")
			?: return@signingConfigs

		create("release") {
			storeFile = rootDir.resolve(keystoreFile)
			storePassword = System.getenv("KEYSTORE_PASSWORD")
			keyAlias = System.getenv("KEY_ALIAS")
			keyPassword = System.getenv("KEY_PASSWORD")

			enableV1Signing = true
			enableV2Signing = true
			enableV3Signing = true
			enableV4Signing = false
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro",
			)

			if (System.getenv("RELEASE") == "true") {
				signingConfig = signingConfigs.getByName("release")
			} else {
				signingConfig = signingConfigs.getByName("debug")
			}
		}
	}
}

dependencies {
	compileOnly("de.robv.android.xposed:api:82")
}
