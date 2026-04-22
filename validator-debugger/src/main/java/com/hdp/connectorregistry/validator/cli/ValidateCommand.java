package com.hdp.connectorregistry.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.validator.Diagnostic;
import com.hdp.connectorregistry.validator.DiagnosticSeverity;
import com.hdp.connectorregistry.validator.ConnectorValidator;
import java.nio.file.Path;
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
        var loadedConnector = new ConnectorLoader().load(connectorPath);
        var config = OBJECT_MAPPER.readTree(configPath.toFile());
        List<Diagnostic> diagnostics = new ConnectorValidator().validate(loadedConnector, config);
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
}
