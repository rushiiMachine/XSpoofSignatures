buildscript {
	repositories {
		google()
		mavenCentral()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.1.4")
	}
}

allprojects {
	repositories {
		google()
		mavenCentral()
		maven(url = "https://api.xposed.info/")
	}
}

tasks.register<Delete>("clean") {
	delete(rootProject.layout.buildDirectory.get())
}
