package com.hengshucredit.rule.server.example;

/** Standalone demo processes do not start a management console or load its signing key. */
public final class ExampleCommands {
    private ExampleCommands() {}
    public static boolean run(String[] args) {
        if (args.length != 1 || !java.util.Set.of("--credit-example-provision", "--credit-example-supplier", "--prepare-credit-example-history").contains(args[0])) return false;
        try {
            if (args[0].equals("--credit-example-provision")) ExampleCredentials.provision();
            else if (args[0].equals("--prepare-credit-example-history")) ExampleHistoryCases.prepare();
            else new ExampleSupplier().run();
        } catch (Exception error) {
            // Never print JDBC arguments, generated passwords or request payloads.
            System.err.println("演示服务启动失败：" + error.getClass().getSimpleName());
            System.exit(1);
        }
        return true;
    }
}
