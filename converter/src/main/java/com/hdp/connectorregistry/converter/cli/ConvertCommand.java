package com.hdp.connectorregistry.converter.cli;

import com.hdp.connectorregistry.converter.AirbyteManifestConverter;
import com.hdp.connectorregistry.converter.ConversionResult;
import com.hdp.connectorregistry.converter.OutputWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "convert", mixinStandardHelpOptions = true)
public final class ConvertCommand implements Callable<Integer> {
    @Option(names = "--input", required = true, description = "Airbyte manifest path")
    private Path input;

    @Option(names = "--output", required = true, description = "Output directory")
    private Path output;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ConvertCommand()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        ConversionResult result = new AirbyteManifestConverter().convert(input);
        new OutputWriter().write(result, output);
        System.out.printf(
                "Converted %s -> %s (status=%s, issues=%d)%n",
                input,
                output,
                result.report().status(),
                result.report().issues().size());
        return 0;
    }
}
