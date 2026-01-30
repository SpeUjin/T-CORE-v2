plugins {
    java
    id("org.springframework.boot") version "3.5.10"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.tcore"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // 검증된 LTS
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") } // Spring AI용
}

// Spring AI 최신 버전 (3.5.10과 가장 잘 맞는 버전)
val springAiVersion = "1.0.0-M5"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

dependencies {
    // [Core & Safety]
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // [Database & High Traffic]
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    implementation("org.redisson:redisson-spring-boot-starter:3.42.0") // 분산 락 필수

    // [AI Ops]
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")

    // [Lombok]
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // [Test]
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}