package com.hdp.connectorregistry.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.hdp.connectorregistry.validator.cli.ListComponentsCommand;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ListComponentsCommandTest {
    @Test
    void listsStreamsSchemasAndSigners() throws Exception {
        var connectorPath = resourcePath("fixtures/connector/minimal/connector.yaml");
        var output = new ByteArrayOutputStream();
        var commandLine = new CommandLine(new ListComponentsCommand());
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("--connector", connectorPath.toString());

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("users")
                .contains("schemas/users.json")
                .contains("fixed_header");
    }

    @Test
    void listsReferencedSchemasEvenWhenSchemaFileIsMissing() throws Exception {
        var connectorPath = resourcePath("fixtures/connector/missing-schema/connector.yaml");
        var output = new ByteArrayOutputStream();
        var commandLine = new CommandLine(new ListComponentsCommand());
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("--connector", connectorPath.toString());

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("users")
                .contains("schemas/missing-users.json");
    }

    @Test
    void listsInlineSchemasUsingStableMarker() throws Exception {
        var connectorPath = resourcePath("fixtures/connector/inline-schema/connector.yaml");
        var output = new ByteArrayOutputStream();
        var commandLine = new CommandLine(new ListComponentsCommand());
        commandLine.setOut(new PrintWriter(output, true, StandardCharsets.UTF_8));

        int exitCode = commandLine.execute("--connector", connectorPath.toString());

        assertThat(exitCode).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("events")
                .contains("inline:events");
    }

    private static Path resourcePath(String resourceName) throws URISyntaxException {
        var resource = ListComponentsCommandTest.class.getClassLoader().getResource(resourceName);
        return Path.of(Objects.requireNonNull(resource, resourceName).toURI());
    }
}
