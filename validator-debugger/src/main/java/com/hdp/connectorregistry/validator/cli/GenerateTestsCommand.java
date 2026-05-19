package com.hdp.connectorregistry.validator.cli;

import com.hdp.connectorregistry.validator.verification.ConnectorTestGenerator;
import com.hdp.connectorregistry.validator.verification.VerificationFailure;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "generate-tests", mixinStandardHelpOptions = true)
public final class GenerateTestsCommand implements Callable<Integer> {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        try {
            var generated = new ConnectorTestGenerator().generate(connectorPath);
            var out = spec.commandLine().getOut();
            if (generated.isEmpty()) {
                out.println("No test files generated; existing files were left unchanged.");
            } else {
                generated.forEach(path -> out.println("generated " + path));
            }
            out.flush();
            return 0;
        } catch (VerificationFailure exception) {
            spec.commandLine().getOut().printf("ERROR GENERATE_TESTS_FAILED %s%n", exception.getMessage());
            spec.commandLine().getOut().flush();
            return 1;
        } catch (RuntimeException exception) {
            spec.commandLine().getOut().printf("ERROR GENERATE_TESTS_FAILED %s%n", exception.getMessage());
            spec.commandLine().getOut().flush();
            return 1;
        }
    }
}
