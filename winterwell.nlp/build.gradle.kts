/**
 * A very simplistic gradle build script
 *
 * To publish to your local ~/.m2 run 'gradle publishToMavenLocal'
 *
 * Definitely not DRY wrt the Bob side of things. Whenever changes are made there
 * to either the version number or dependencies they need to be replicated here.
 */

tasks.wrapper {
    gradleVersion = "7.4"
}

plugins {
    `java-library`
    `maven-publish`
}

group = "com.winterwell"
version = "1.3.2"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.winterwell","utils","1.3.2")
    implementation("com.winterwell","web","1.1.4")
    implementation("com.winterwell","maths","1.1.0")
    implementation("com.winterwell","depot","1.1.0")

    implementation("org.apache.commons", "commons-compress", "1.20")
    implementation("org.apache.commons","commons-lang3","3.9")
    implementation("com.googlecode.matrix-toolkits-java", "mtj", "1.0.4")
    implementation("net.sf.trove4j", "trove4j", "3.0.3")

    implementation(files("lib/stemmer/stemmer.jar"))
    implementation(files("lib/jwnl/jwnl.jar"))
    implementation(files("lib/fasttag/fasttag.jar"))
    implementation(files("lib/cld/cld.jar"))

    implementation("junit","junit","4.13.2")
}

java.sourceSets["main"].java {
    srcDir("src")
}

java.sourceSets["test"].java {
    srcDir("test")
    srcDir("test.utils")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}