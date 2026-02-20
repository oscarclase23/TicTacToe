import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.dam.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb,TargetFormat.Exe)
            packageName = "org.dam.project"
            packageVersion = "1.0.0"
        }
    }
}

// Register runServer task
// Register runServer task
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Runs the Game Server"
    
    // 1. Get the target safely
    val jvmTarget = kotlin.targets.getByName("jvm")
    val mainCompilation = jvmTarget.compilations.getByName("main")
    
    // 2. Build classpath safely
    val runtimeDeps = mainCompilation.runtimeDependencyFiles ?: files()
    classpath = files(mainCompilation.output.allOutputs, runtimeDeps)
    
    // 3. Set Main Class
    mainClass.set("org.dam.project.server.GameServerKt") 
    
    // 4. Enable interactive console
    standardInput = System.`in`
    
    // 5. Pass arguments if any
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}
