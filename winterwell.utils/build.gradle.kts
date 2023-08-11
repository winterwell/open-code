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
}

dependencies {
    implementation("com.thoughtworks.xstream","xstream","1.4.19")
    implementation("org.ogce","xpp3","1.1.6")
    implementation("dnsjava","dnsjava","2.1.9")
    testImplementation("junit","junit","4.13.2")
}

java.sourceSets["main"].java {
    srcDir("src")
}

java.sourceSets["test"].java {
    srcDir("test")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}