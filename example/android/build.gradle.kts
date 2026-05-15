buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("org.apache.commons:commons-compress:1.28.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.layout.buildDirectory = file("../build")

subprojects {
    project.layout.buildDirectory =
        file("${rootProject.layout.buildDirectory.get().asFile}/${project.name}")
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
