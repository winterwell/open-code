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
version = "1.1.0"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.winterwell","utils","1.3.2")
    implementation("com.winterwell","web","1.1.4")

    implementation("net.sf.trove4j", "trove4j", "3.0.3")
    implementation("com.google.guava", "guava", "28.2-jre")
    implementation("org.apache.commons", "commons-math3", "3.6.1")
    implementation("com.googlecode.matrix-toolkits-java", "mtj", "1.0.4")
    implementation("com.thoughtworks.xstream","xstream","1.4.19")

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