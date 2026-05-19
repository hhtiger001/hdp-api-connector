import org.gradle.api.tasks.JavaExec

plugins {
    application
}

dependencies {
    implementation(project(":connector-model"))
    implementation(project(":sync-runtime-example"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.27.2")
}

application {
    mainClass.set("com.hdp.connectorregistry.validator.cli.Main")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
