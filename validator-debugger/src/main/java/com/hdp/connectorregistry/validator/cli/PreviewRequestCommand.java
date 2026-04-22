package com.hdp.connectorregistry.validator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdp.connectorregistry.validator.RawConnectorLoader;
import com.hdp.connectorregistry.validator.RequestPlanner;
import com.hdp.connectorregistry.validator.RequestPreview;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "preview-request", mixinStandardHelpOptions = true)
public final class PreviewRequestCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Option(names = "--connector", required = true)
    Path connectorPath;

    @Option(names = "--stream", required = true)
    String streamName;

    @Option(names = "--config", required = true)
    Path configPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var loadedConnector = new RawConnectorLoader().load(connectorPath);
        var config = OBJECT_MAPPER.readTree(configPath.toFile());
        RequestPreview preview = new RequestPlanner().preview(loadedConnector, streamName, config);
        printPreview(preview);
        return 0;
    }

    private void printPreview(RequestPreview preview) {
        var out = spec.commandLine().getOut();
        out.printf("%s %s qps=%s%n", preview.method(), preview.url(), preview.effectiveQps());
        printMap(out, "headers", preview.headers());
        printMap(out, "queryParameters", preview.queryParameters());
        if (preview.body() != null) {
            out.println("body:");
            out.println(preview.body());
        }
        if (preview.signerName() != null) {
            out.println("signer=" + preview.signerName());
        }
        out.flush();
    }

    private void printMap(java.io.PrintWriter out, String title, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        out.println(title + ":");
        values.forEach((key, value) -> out.println(key + "=" + value));
    }
}
