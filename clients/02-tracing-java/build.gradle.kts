import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    java
    id("org.springframework.boot") version "3.1.4"
    id("io.spring.dependency-management") version "1.1.3"
    id("io.freefair.lombok") version "8.3"
}

group = "dev.kalli"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("kalli.dev/${project.name}:${project.version}")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    gradlePluginPortal()
}

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.opentelemetry:opentelemetry-api:1.30.1") // Upstream spring version 1.25.0 do not include logg providers, fails on startup

    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    implementation("io.opentelemetry.instrumentation:opentelemetry-okhttp-3.0:1.30.0-alpha")
    implementation("org.springframework.experimental.ai:spring-ai-openai-spring-boot-starter:0.2.0-SNAPSHOT")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
