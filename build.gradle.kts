import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:2.0.0")
    }
}
plugins {
    kotlin("jvm") version "2.2.0"
    signing
    id("maven-publish")
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.vanniktech.maven.publish") version "0.31.0"
}

group = "io.github.yangentao"

version = "2.0.8"
val artifactName = "harenetty"
val githubLib = "harenetty"
val descLib = "Java/Kotlin web, Netty."


repositories {
    mavenLocal()
    mavenCentral()
//    maven("https://app800.cn/maven/repository/public/")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))

    api("io.netty:netty-all:4.1.119.Final")
    api("io.github.yangentao:hare:[2.0.8,)")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(project.group.toString(), artifactName, project.version.toString())
    pom {
        configPom(this, artifactName, descLib, githubLib)
    }
}
afterEvaluate {
    publishing {
        repositories {
            mavenLocal()
            maven {
                name = "App800"
                url = uri("https://app800.cn/maven/repository/public/")
                credentials {
                    username = providers.gradleProperty("ARCHIVA_USERNAME").get()
                    password = providers.gradleProperty("ARCHIVA_PASSWORD").get()
                }
            }
        }
    }
}



fun configPom(pom: MavenPom, artifactName: String, descLib: String, githubLib: String = artifactName) {
    pom.apply {
        name.set(artifactName)
        description.set(descLib)
        inceptionYear.set("2025")
        url.set("https://github.com/yangentao/$githubLib/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("yangentao")
                name.set("Yang Entao")
                url.set("https://github.com/yangentao/")
            }
        }
        scm {
            url.set("https://github.com/yangentao/$githubLib/")
            connection.set("scm:git:git://github.com/yangentao/$githubLib.git")
            developerConnection.set("scm:git:ssh://git@github.com/yangentao/$githubLib.git")
        }
    }
}