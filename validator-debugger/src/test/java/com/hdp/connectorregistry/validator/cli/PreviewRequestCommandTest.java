package com.hdp.connectorregistry.validator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class PreviewRequestCommandTest {
    @Test
    void usesRequestLevelQpsOverStreamAndDefaults() throws Exception {
        var commandLine = new CommandLine(new PreviewRequestCommand());
        var output = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute(
                "--connector",
                resourcePath("fixtures/connector/request-qps/connector.yaml").toString(),
                "--stream",
                "users",
                "--config",
                resourcePath("fixtures/config/preview-config.json").toString());

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("GET https://api.example.com/users qps=7")
                .contains("X-Signature=signed");
    }

    @Test
    void previewsRequestWhenSchemaFileIsMissing() throws Exception {
        var commandLine = new CommandLine(new PreviewRequestCommand());
        var output = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute(
                "--connector",
                resourcePath("fixtures/connector/missing-schema/connector.yaml").toString(),
                "--stream",
                "users",
                "--config",
                resourcePath("fixtures/config/preview-config.json").toString());

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("GET https://api.example.com/users qps=2")
                .contains("X-Signature=signed");
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = PreviewRequestCommandTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
