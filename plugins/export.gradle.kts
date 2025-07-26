// export.gradle.kts

import java.io.File
import java.util.Properties
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip

fun sdkRootDir(rootProject: org.gradle.api.Project): File {
    val lp = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    lp.getProperty("sdk.dir")?.let { return File(it) }
    System.getenv("ANDROID_SDK_ROOT")?.let { return File(it) }
    System.getenv("ANDROID_HOME")?.let { return File(it) }
    error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_SDK_ROOT.")
}

fun latestBuildTools(sdk: File): File {
    val dir = File(sdk, "build-tools")
    val all = dir.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
    require(all.isNotEmpty()) { "No build-tools in $dir" }
    return all.first()
}

fun findCompileSdk(project: org.gradle.api.Project): Int {
    val ext = project.extensions.getByName("android")
    val method = ext.javaClass.methods.firstOrNull { it.name == "getCompileSdk" }
        ?: error("Couldn't find getCompileSdk on android extension.")
    return (method.invoke(ext) as Number).toInt()
}

val sdkDir = sdkRootDir(rootProject)
val buildToolsDir = latestBuildTools(sdkDir)
val d8Exe = File(buildToolsDir, if (org.gradle.internal.os.OperatingSystem.current().isWindows) "d8.bat" else "d8")
val compileSdk = findCompileSdk(project)
val androidJarFile = File(sdkDir, "platforms/android-$compileSdk/android.jar")

val dexWorkDir = layout.buildDirectory.dir("outputs/pluginDex")
val tmpDir = layout.buildDirectory.dir("tmp/pluginDex")

fun locateClassesJar(project: org.gradle.api.Project): File {
    val buildDir = project.layout.buildDirectory.get().asFile
    val aar = File(buildDir, "outputs/aar/${project.name}-release.aar")
    if (aar.exists()) return aar
    val candidates = project.fileTree(buildDir) {
        include("**/release/**/classes.jar")
    }.files.sortedByDescending { it.lastModified() }
    return candidates.firstOrNull()
        ?: error("Could not find classes.jar or AAR in $buildDir. Run assembleRelease first.")
}

val extractClassesJar by tasks.registering(Copy::class) {
    dependsOn("assembleRelease")
    val src = providers.provider { locateClassesJar(project) }
    from({
        val f = src.get()
        if (f.extension == "aar") zipTree(f) else f
    })
    include("classes.jar")
    into(tmpDir)
    doLast { println(">> Extracted: ${tmpDir.get().asFile.resolve("classes.jar")}") }
}

val makeDex by tasks.registering(Exec::class) {
    dependsOn(extractClassesJar)
    doFirst {
        require(d8Exe.exists()) { "d8 not found: $d8Exe" }
        require(androidJarFile.exists()) { "android.jar not found: $androidJarFile" }
        dexWorkDir.get().asFile.mkdirs()
    }
    val classesJar = tmpDir.get().asFile.resolve("classes.jar")
    commandLine(
        d8Exe.absolutePath,
        "--release",
        "--min-api", "26",
        "--lib", androidJarFile.absolutePath,
        "--output", dexWorkDir.get().asFile.absolutePath,
        classesJar.absolutePath
    )
    doLast {
        val dex = dexWorkDir.get().asFile.resolve("classes.dex")
        println(">> d8 output expected at: $dex")
        if (!dex.exists()) error("d8 finished but no classes.dex was produced. Run with --info.")
    }
}

val packDexJar by tasks.registering(Zip::class) {
    dependsOn(makeDex)
    archiveFileName.set("plugin.dex.jar")
    destinationDirectory.set(dexWorkDir)
    from(dexWorkDir) { include("classes.dex") }
    doLast { println(">> Created: ${destinationDirectory.get().asFile.resolve(archiveFileName.get())}") }
}

tasks.register("buildPluginDexJar") {
    group = "build"
    description = "Builds plugin.dex.jar (jar containing classes.dex) for dynamic loading."
    dependsOn(packDexJar)
}
