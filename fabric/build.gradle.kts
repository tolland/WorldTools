import groovy.lang.Closure

apply(from = rootProject.file("gradle/secrets.gradle"))

architectury {
    platformSetupLoomIde()
    fabric()
}

base.archivesName.set("${base.archivesName.get()}-fabric")

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    enableTransitiveAccessWideners.set(true)
    runs {
        getByName("client") {
            ideConfigGenerated(true)
        }
    }
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentFabric"].extendsFrom(this)
}

dependencies {
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionFabric")) { isTransitive = false }
    modImplementation("net.fabricmc:fabric-loader:${project.properties["fabric_loader_version"]!!}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.properties["fabric_api_version"]!!}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.properties["fabric_kotlin_version"]!!}")
    modApi("me.shedaniel.cloth:cloth-config-fabric:${project.properties["cloth_config_version"]}")
    modCompileOnly("com.terraformersmc:modmenu:${project.properties["mod_menu_version"]}")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(getProperties())
            expand(mutableMapOf("version" to project.version))
        }
    }

    remapJar {
        injectAccessWidener.set(true)
    }
}

val getSecret = project.extra["getSecret"] as Closure<*>

val githubActor = getSecret.call("GITHUB_ACTOR") as String
val githubToken = getSecret.call("GITHUB_TOKEN") as String

publishing {
    publications {
        create<MavenPublication>("gpr") {
            groupId = "worldtools"
            artifactId = "worldtools-fabric"
            version = "${project.properties["mod_version"]}-SNAPSHOT"

            artifact(tasks.named("remapJar"))

//                    pom {
//                        name.set("baritone (patched)")
//                        description.set("baritone patched 1.21.8")
//                        url.set("https://github.com/tolland/baritone")
//                    }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/tolland/WorldTools")
            credentials {
                username = githubActor
                password = githubToken
            }
        }
    }
}
