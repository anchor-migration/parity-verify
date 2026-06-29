package com.anchor.migration.parityverify;

import com.anchor.migration.parityverify.cli.CompareCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
        name = "parity-verify",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        subcommands = {CompareCommand.class})
public final class Cli implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Cli()).execute(args);
        System.exit(exit);
    }
}
