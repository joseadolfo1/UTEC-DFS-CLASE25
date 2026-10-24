import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.shopeasy"
version = "0.0.1-SNAPSHOT"
description = "ShopEasy - Proyecto Ecommerce MVP para curso Fullstack"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // API REST, JPA + Hibernate y Spring Security
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // ModelMapper: convierte entidades a DTOs
    implementation("org.modelmapper:modelmapper:3.2.6")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Swagger / OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Lombok: reduce codigo repetitivo (getters, setters, constructores)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Driver de MySQL
    runtimeOnly("com.mysql:mysql-connector-j")

    // Pruebas: H2 en memoria reemplaza a MySQL (perfil "test"), no hace falta Docker
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Todas las tareas de prueba usan JUnit 5 y, si una falla, muestran en la consola
// (y en el log de GitHub Actions) que se esperaba y que se obtuvo.
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// "gradlew test" (y "gradlew build") corre las 98 pruebas y deja el informe de
// cobertura en build/reports/jacoco/test/html/index.html
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

// "gradlew unitTest" o "gradlew integrationTest" filtran por @Tag
listOf("unit", "integration").forEach { etiqueta ->
    tasks.register<Test>("${etiqueta}Test") {
        description = "Corre solo las pruebas con @Tag(\"$etiqueta\")."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { includeTags(etiqueta) }
    }
}

// Solo el JAR ejecutable (bootJar) en build/libs: asi "COPY build/libs/*.jar" encuentra uno solo.
tasks.jar {
    enabled = false
}
