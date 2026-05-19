package com.hdp.connectorregistry.validator.cli;

import com.hdp.connectorregistry.validator.verification.ConnectorVerificationRunner;
import com.hdp.connectorregistry.validator.verification.VerificationFailure;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "verify-connector", mixinStandardHelpOptions = true)
public final class VerifyConnectorCommand implements Callable<Integer> {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Option(names = "--config", required = true)
    Path configPath;

    @Option(names = "--tool")
    String toolName;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        try {
            var results = new ConnectorVerificationRunner().verify(connectorPath, configPath, toolName);
            var out = spec.commandLine().getOut();
            results.forEach(result -> out.printf(
                    "OK %s tool=%s method=%s status=%s url=%s steps=%s%n",
                    result.scenario(),
                    result.tool(),
                    result.method(),
                    result.statusCode(),
                    result.url(),
                    String.join(",", result.passedSteps())));
            out.flush();
            return 0;
        } catch (VerificationFailure exception) {
            spec.commandLine().getOut().printf("ERROR VERIFY_CONNECTOR_FAILED %s%n", exception.getMessage());
            spec.commandLine().getOut().flush();
            return 1;
        } catch (RuntimeException exception) {
            spec.commandLine().getOut().printf("ERROR VERIFY_CONNECTOR_FAILED %s%n", exception.getMessage());
            spec.commandLine().getOut().flush();
            return 1;
        }
    }
}
