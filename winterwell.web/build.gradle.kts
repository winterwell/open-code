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
version = "1.1.4"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.winterwell","utils","1.3.2")
    implementation("com.winterwell","flexi-gson","1.2.2")

    implementation("org.eclipse.jetty", "jetty-server", "10.0.7")
    implementation("org.eclipse.jetty","jetty-util","10.0.7")
    implementation("org.eclipse.jetty","jetty-util-ajax","10.0.7")
    implementation("org.eclipse.jetty", "jetty-servlet","10.0.7")
    implementation("com.sun.mail", "jakarta.mail", "2.0.1")
    implementation("com.sun.mail", "gimap", "2.0.1")
    implementation("eu.medsea.mimeutil", "mime-util", "1.3")
    implementation("org.apache.httpcomponents", "httpclient", "4.5.10")
    implementation("org.ccil.cowan.tagsoup", "tagsoup", "1.2.1")
    implementation("commons-fileupload","commons-fileupload","1.4")
    implementation("commons-io", "commons-io", "2.6")
    implementation("org.apache.commons","commons-lang3","3.9")
    implementation("dnsjava", "dnsjava", "2.1.9")

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