package com.hdp.connectorregistry.validator.cli;

import com.hdp.connectorregistry.io.ConnectorFileBuilder;
import com.hdp.connectorregistry.io.ConnectorLoadException;
import com.hdp.connectorregistry.io.ConnectorObjectMapperFactory;
import com.hdp.connectorregistry.validator.RawConnectorLoader;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "build-connector", mixinStandardHelpOptions = true)
public final class BuildConnectorCommand implements Callable<Integer> {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        try {
            var loadedConnector = new RawConnectorLoader().load(connectorPath);
            var connector = new ConnectorFileBuilder().build(loadedConnector);
            ConnectorObjectMapperFactory.jsonMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(spec.commandLine().getOut(), connector);
            spec.commandLine().getOut().println();
            spec.commandLine().getOut().flush();
            return 0;
        } catch (ConnectorLoadException exception) {
            spec.commandLine().getOut().printf("ERROR CONNECTOR_LOAD_FAILED %s%n", exception.getMessage());
            spec.commandLine().getOut().flush();
            return 1;
        }
    }
}
