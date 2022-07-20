plugins {
    id("java-library")
    id("maven-publish")
}

group = "io.elmas"
version = "1.0-SNAPSHOT"

repositories {
    maven(url = "https://maven.aliyun.com/repositories/public/")
    mavenLocal()
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.elmas"
            artifactId = "elmas-classloader"
            version = "1.0-SNAPSHOT"

            from(components["java"])
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
