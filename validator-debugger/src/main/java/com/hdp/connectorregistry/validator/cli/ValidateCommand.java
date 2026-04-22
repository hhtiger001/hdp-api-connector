package com.hdp.connectorregistry.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.io.SchemaResolutionException;
import com.hdp.connectorregistry.validator.Diagnostic;
import com.hdp.connectorregistry.validator.DiagnosticSeverity;
import com.hdp.connectorregistry.validator.ConnectorValidator;
import com.hdp.connectorregistry.io.ConnectorLoader.LoadedConnector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "validate", mixinStandardHelpOptions = true)
public final class ValidateCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Option(names = "--config", required = true)
    Path configPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        List<Diagnostic> diagnostics = new ArrayList<>();
        LoadedConnector loadedConnector;
        try {
            loadedConnector = new ConnectorLoader().load(connectorPath);
        } catch (IllegalStateException exception) {
            if (isSchemaResolutionFailure(exception)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticSeverity.ERROR,
                        "SCHEMA_LOAD_FAILED",
                        schemaLoadMessage(exception)));
            } else {
                throw exception;
            }
            loadedConnector = null;
        }

        if (loadedConnector != null) {
            var config = OBJECT_MAPPER.readTree(configPath.toFile());
            diagnostics.addAll(new ConnectorValidator().validate(loadedConnector, config));
        }

        diagnostics.forEach(diagnostic -> printDiagnostic(spec.commandLine().getOut(), diagnostic));
        boolean hasError = diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
        if (!hasError) {
            spec.commandLine().getOut().println("OK");
        }
        spec.commandLine().getOut().flush();
        return hasError ? 1 : 0;
    }

    private static void printDiagnostic(java.io.PrintWriter out, Diagnostic diagnostic) {
        out.printf("%s %s %s%n", diagnostic.severity(), diagnostic.code(), diagnostic.message());
    }

    private static boolean isSchemaResolutionFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SchemaResolutionException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static String schemaLoadMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SchemaResolutionException && cursor.getMessage() != null) {
                return cursor.getMessage();
            }
            cursor = cursor.getCause();
        }
        return throwable.getMessage() == null ? "Unable to load schema" : throwable.getMessage();
    }
}
