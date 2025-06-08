package io.github.ayaxperson.zvukcomdownloader;

import io.github.ayaxperson.zvukcomdownloader.action.DownloadDiscography;
import io.github.ayaxperson.zvukcomdownloader.action.DownloadTrack;
import io.github.ayaxperson.zvukcomdownloader.api.Zvuk;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static final List<Path> TEMPORARY_FILES = new ArrayList<>();

    public static void main(final String[] args) {
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted((() -> {
            System.out.println("Closing http client");

            try {
                Zvuk.close();
            } catch (final Exception e) {
                System.err.println("Error closing HTTP client (You can probably ignore this)");
                System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            }

            System.out.println("Deleting temp files");

            TEMPORARY_FILES.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("Failed to delete temp file");
                    System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
                }
            });
        })));

        final Options options = new Options();

        options.addOption(Option.builder()
                .argName("Download track (parameter ID)")
                .option("t")
                .longOpt("track")
                .hasArg()
                .build());
        options.addOption(Option.builder()
                .argName("Download discography (parameter ID)")
                .option("d")
                .longOpt("discography")
                .hasArg()
                .build());
        options.addOption(Option.builder()
                .argName("Authentication Token")
                .option("a")
                .longOpt("authToken")
                .hasArg()
                .build());

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (!cmd.hasOption("authToken")) {
                System.err.println("Missing required args");
                System.exit(-1);
            }

            final String authToken = cmd.getOptionValue("authToken");

            final List<Runnable> tasks = new ArrayList<>();

            if (cmd.hasOption("discography"))
                tasks.add(new DownloadDiscography(cmd.getOptionValue("discography"), authToken));

            if (cmd.hasOption("track"))
                tasks.add(new DownloadTrack(cmd.getOptionValue("track"), authToken));

            if (!tasks.isEmpty()) {
                for (int i = 0; i < tasks.size(); i++) {
                    System.out.printf("Running task %d%n", i);
                    tasks.get(i).run();
                }
            } else {
                System.out.println("There is nothing to do");
            }
        } catch (final ParseException e) {
            System.err.println("Failed to parse arguments");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
        }
    }




}
