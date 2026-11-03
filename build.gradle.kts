plugins {
    alias(libs.plugins.fabric.loom)
}

loom {
    mixin {
        defaultRefmapName = "Tim.refmap.json"
        useLegacyMixinAp = true
    }
}

// Read the target version dynamically, falling back to 1.21.4 if none is specified
val mcVersion = providers.gradleProperty("minecraft_version").orElse("1.21.4").get()
val versionSourceSet = "v${mcVersion.replace('.', '_')}"

// Map each Minecraft minor version to its exact official Yarn build mappings
val yarnVersion = mapOf(
    "1.21.4" to "1.21.4+build.3",
    "1.21.5" to "1.21.5+build.1",
    "1.21.8" to "1.21.8+build.1",
    "1.21.11" to "1.21.11+build.1"
)[mcVersion] ?: error("Unsupported minecraft_version '$mcVersion'. Supported versions: 1.21.4, 1.21.5, 1.21.8, 1.21.11")
val xaeroWorldMapVersion = when (mcVersion) {
    "1.21.4" -> "1.39.2_Fabric_1.21.4"
    "1.21.5" -> "1.39.12_Fabric_1.21.5"
    "1.21.8" -> "1.39.13_Fabric_1.21.8"
    "1.21.11" -> "1.40.2_Fabric_1.21.11"
    else -> error("Unsupported minecraft_version '$mcVersion'")
}
val xaeroPlusVersion = when (mcVersion) {
    "1.21.4" -> "2.25+fabric-1.21.4"
    "1.21.5", "1.21.8" -> "2.29.1+fabric-$mcVersion"
    "1.21.11" -> "2.30.0+fabric-1.21.11"
    else -> error("Unsupported minecraft_version '$mcVersion'")
}

sourceSets {
    main {
        java.srcDir("src/$versionSourceSet/java")
        resources.srcDir("src/$versionSourceSet/resources")
        if (mcVersion == "1.21.5" || mcVersion == "1.21.8") {
            java.srcDir("src/v1_21_5_plus/java")
        }
    }
}

base {
    archivesName = properties["archives_base_name"] as String
    version = "${providers.gradleProperty("mod_version").orElse(libs.versions.mod.version).get()}+mc$mcVersion"
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "Cabaletta (Baritone)"
        url = uri("https://pkgs.dev.azure.com/cabaletta/baritone/_packaging/baritone/maven/v1")
    }
}

dependencies {
    // Explicitly reference the full dependency string notation to satisfy Kotlin DSL parser
    add("minecraft", "com.mojang:minecraft:$mcVersion")
    add("mappings", "net.fabricmc:yarn:$yarnVersion:v2")
    modImplementation(libs.fabric.loader)
    annotationProcessor("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7")

    // Meteor Client
    modImplementation(libs.meteor.client)

    // Baritone API
    modImplementation(files("libs/baritone-api-fabric-1.13.1.jar"))

    // Xaero's World Map & XaeroPlus mapped safely per version target
    modCompileOnly("maven.modrinth:xaeros-world-map:$xaeroWorldMapVersion")
    modCompileOnly("maven.modrinth:xaeroplus:$xaeroPlusVersion")
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to mcVersion
        )

        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}