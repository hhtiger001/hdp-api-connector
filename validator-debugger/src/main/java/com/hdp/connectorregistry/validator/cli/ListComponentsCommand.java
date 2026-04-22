package com.hdp.connectorregistry.validator.cli;

import com.hdp.connectorregistry.io.ConnectorLoader;
import com.hdp.connectorregistry.validator.RequestPlanner;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "list-components", mixinStandardHelpOptions = true)
public final class ListComponentsCommand implements Callable<Integer> {
    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        var loadedConnector = new ConnectorLoader().load(connectorPath);
        spec.commandLine().getOut().print(new RequestPlanner().listComponents(loadedConnector));
        spec.commandLine().getOut().flush();
        return 0;
    }
}
