package io.github.alexshamrai.scenario.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Lets fixtures that run in separate JVMs wait for each other through marker files in the shared working
 * directory, so that the JVMs finish at about the same time and write the report concurrently.
 */
public final class ForkBarrier {

    private ForkBarrier() {
    }

    /**
     * Marks this JVM as ready, then waits until {@code parties} JVMs are.
     *
     * @param name    a name unique to this JVM
     * @param parties how many JVMs to wait for
     */
    public static void await(String name, int parties) throws IOException, InterruptedException {
        Files.createFile(Path.of(name + ".ready"));
        long deadline = System.currentTimeMillis() + 60_000;
        while (ready() < parties) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Only " + ready() + " of " + parties + " JVMs arrived");
            }
            Thread.sleep(5);
        }
    }

    private static long ready() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("."))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".ready")).count();
        }
    }
}
