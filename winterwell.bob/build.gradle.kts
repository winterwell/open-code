/**
 * A very simplistic gradle build script
 *
 * NOTE: this is intentionally limited to providing Bob as a library
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
version = "1.4.5"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.winterwell","utils","1.3.2")
    implementation("com.winterwell","web","1.1.4")

    implementation("commons-net","commons-net","3.6")
    implementation("com.jcraft","jsch","0.1.55")
    implementation("com.sun.mail", "jakarta.mail", "2.0.1")
    implementation("org.eclipse.jetty.toolchain", "jetty-servlet-api", "4.0.6")

    implementation("junit","junit","4.13.2")
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