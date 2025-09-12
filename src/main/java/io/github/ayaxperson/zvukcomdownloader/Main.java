package io.github.ayaxperson.zvukcomdownloader;

import io.github.ayaxperson.zvukcomdownloader.action.DownloadAlbum;
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

    public static final String DEFAULT_API_VERSION = "v3.3.9";

    public static final List<Path> TEMPORARY_FILES = new ArrayList<>();

    public static void main(final String[] args) {
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted((() -> {

            try {
                if (Zvuk.close())
                    System.out.println("Closing http client");
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
                .desc("The ID of the track you want to download (if you want to download a track, that is)")
                .hasArg()
                .build());
        options.addOption(Option.builder()
                .argName("Download discography (parameter ID)")
                .option("d")
                .longOpt("discography")
                .desc("The ID of the artist you want to download the discography of (if you want to download an artist's discography, that is)")
                .hasArg()
                .build());
        options.addOption(Option.builder()
                .argName("Download release (album) (parameter ID)")
                .option("r")
                .longOpt("release")
                .desc("The ID of the album you want to download (if you want to download an album, that is)")
                .hasArg()
                .build());
        options.addOption(Option.builder()
                .argName("Authentication Token")
                .option("a")
                .longOpt("authToken")
                .desc("Zvuk account authentication token")
                .required(true)
                .hasArg()
                .build());
        options.addOption(Option.builder()
                .argName("API version")
                .option("v")
                .longOpt("apiVersion")
                .desc("Zvuk API Version - Use this if the default is not working. The API version changes frequently and it's possible this utility is not updated so as to reflect it.")
                .hasArg()
                .build());

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                new HelpFormatter().printHelp("Zvuk.com downloader", options);
                System.exit(0);
            }

            final String authToken = cmd.getOptionValue("authToken");

            final String apiVersion;

            if (cmd.hasOption("apiVersion")) {
                apiVersion = cmd.getOptionValue("apiVersion");
            } else {
                apiVersion = DEFAULT_API_VERSION;
            }

            final List<Runnable> tasks = new ArrayList<>();

            if (cmd.hasOption("discography"))
                tasks.add(new DownloadDiscography(cmd.getOptionValue("discography"), authToken, apiVersion));

            if (cmd.hasOption("track"))
                tasks.add(new DownloadTrack(cmd.getOptionValue("track"), authToken, apiVersion));

            if (cmd.hasOption("release"))
                tasks.add(new DownloadAlbum(cmd.getOptionValue("release"), authToken, apiVersion));

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
            new HelpFormatter().printHelp("Zvuk.com downloader", options);
            System.exit(0);
        }
    }




}
