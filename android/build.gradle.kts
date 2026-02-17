allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    afterEvaluate {
        if (project.extensions.findByName("android") != null) {
             val android = project.extensions.findByName("android")!!
             try {
                 val getNs = android.javaClass.getMethod("getNamespace")
                 if (getNs.invoke(android) == null) {
                     val setNs = android.javaClass.getMethod("setNamespace", String::class.java)
                     if (project.name == "on_audio_query_android") {
                         setNs.invoke(android, "com.lucasjosino.on_audio_query")
                     }
                 }
             } catch (e: Exception) {
                 // ignore
             }
        }
    }
}
