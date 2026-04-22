import org.gradle.api.tasks.JavaExec

plugins {
    application
}

dependencies {
    implementation(project(":connector-model"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.2")
}

application {
    mainClass.set("com.hdp.connectorregistry.converter.cli.ConvertCommand")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
