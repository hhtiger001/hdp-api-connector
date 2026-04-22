package com.hdp.connectorregistry.validator.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "validator-debugger",
        mixinStandardHelpOptions = true,
        subcommands = {
            ValidateCommand.class,
            ListComponentsCommand.class,
            PreviewRequestCommand.class
        })
public final class Main implements Runnable {
    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
