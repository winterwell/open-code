/**
 * A very simplistic gradle build script
 *
 * To publish to your local ~/.m2 run 'gradle publishToMavenLocal'
 *
 * TODO currently ignores the separately versioned compressor and reindexer targets
 *
 * Definitely not DRY wrt the Bob side of things. Whenever changes are made there
 * to either the version number or dependencies they need to be replicated here.
 */

tasks.wrapper {
    gradleVersion = "7.4"
}

// TODO the application side of things!
plugins {
    `java-library`
    `maven-publish`
}

group = "com.winterwell"
version = "1.2.7"

repositories {
    mavenCentral()
    // TODO sticking to the ua_parser version mentioned in BuildDatalog for now
    // TODO but if we nudge up to v1.4.0+ we could drop this extra repo
    maven {url = uri("https://maven.twttr.com/") }
    mavenLocal()
}

dependencies {
    implementation("com.winterwell","utils","1.3.2")
    implementation("com.winterwell","flexi-gson","1.2.2")
    implementation("com.winterwell","web","1.1.4")
    implementation("com.winterwell","youagain-java-client","1.0.7")
    implementation("com.winterwell","elasticsearch-java-client","1.1.0-ES7-8")
    implementation("com.winterwell","webappbase","1.1.0")
    implementation("com.winterwell","maths","1.1.0")
    implementation("com.winterwell","depot","1.1.0")

    // TODO in future maybe use com.github.ua-parser:uap-java:1.5.0 instead?
    implementation("ua_parser", "ua-parser", "1.3.0")

    implementation("com.google.guava", "guava", "28.2-jre")
    implementation("org.eclipse.jetty.toolchain", "jetty-servlet-api", "4.0.6")
    implementation("org.eclipse.jetty", "jetty-util-ajax", "10.0.7")
    implementation("com.googlecode.matrix-toolkits-java", "mtj", "1.0.4")

    testImplementation("junit","junit","4.13.2")
}

java.sourceSets["main"].java {
    srcDir("src/java")
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