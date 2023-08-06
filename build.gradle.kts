buildscript {
	repositories {
		google()
		mavenCentral()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.1.0")
	}
}

allprojects {
	repositories {
		google()
		mavenCentral()
		maven(url = "https://jcenter.bintray.com/")
	}
}

tasks.register<Delete>("clean") {
	delete(rootProject.buildDir)
}
