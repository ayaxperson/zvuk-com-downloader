package io.github.ayaxperson.zvukcomdownloader;

import com.mpatric.mp3agic.ID3v23Tag;
import com.mpatric.mp3agic.Mp3File;
import io.github.ayaxperson.zvukcomdownloader.api.Zvuk;
import io.github.ayaxperson.zvukcomdownloader.api.Album;
import io.github.ayaxperson.zvukcomdownloader.api.Track;
import io.github.ayaxperson.zvukcomdownloader.api.Artist;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static final List<Path> TEMP_FILES = new ArrayList<>();

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

            TEMP_FILES.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("Failed to delete temp file");
                    System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
                }
            });
        })));

        final Options options = new Options();

        options.addOption("id", true, "ID");
        options.addOption("action", true, "Action (downloadTrack, downloadDiscography)");
        options.addOption("authToken", true, "Authentication Token");

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (!cmd.hasOption("id") || !cmd.hasOption("action") || !cmd.hasOption("authToken")) {
                System.err.println("Missing required args");
                System.exit(-1);
            }

            final String id = cmd.getOptionValue("id");
            final String action = cmd.getOptionValue("action");
            final String authToken = cmd.getOptionValue("authToken");

            switch (action) {
                case "downloadTrack" -> downloadTrack(id, authToken);
                case "downloadDiscography" -> downloadDiscography(id, authToken);

                default -> {
                    System.err.println("No such action exists");
                    System.exit(-1);
                }
            }
        } catch (ParseException e) {
            System.err.println("Failed to parse arguments");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static final DecimalFormat FORMAT_TO_2_DECIMALS = new DecimalFormat("##");

    private static void downloadTrack(final String id, final String authToken) {
        System.out.println("Downloading temp mp3");
        final Path tempPath = Path.of(String.format("temp-%s-%d.mp3", id, Math.round(Math.random() * 10000)));
        TEMP_FILES.add(tempPath);
        try {
            Zvuk.downloadTrack(authToken, id, tempPath);
        } catch (final Exception e) {
            System.err.println("Failed to download file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        writeTrackData(id, tempPath);
    }

    private static void downloadDiscography(final String id, final String authToken) {
        System.out.println("Fetching tracks");
        Track[] tracks;

        try {
            tracks = Zvuk.fetchTracks(id, authToken);
        } catch (final Exception e) {
            System.err.println("Failed to fetch tracks");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        System.out.println("Assigning temp paths to tracks");
        final Map<String, Path> trackPathMap = new HashMap<>();

        for (final Track track : tracks) {
            final Path tempPath = Path.of(String.format("temp-%s-%d.mp3", id, Math.round(Math.random() * 10000)));
            TEMP_FILES.add(tempPath);
            trackPathMap.put(track.id(), tempPath);
        }

        System.out.println("Downloading tracks");
        try {
            Zvuk.downloadTracks(authToken, trackPathMap);
        } catch (final Exception e) {
            System.err.println("Failed to download file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        for (final Track track : tracks) {
            // Not reusing the track object here is not a mistake as this track object does not have the necessary data
            writeTrackData(track.id(), trackPathMap.get(track.id()));
        }
    }

    private static void writeTrackData(final String id, final Path path) {
        System.out.println("Fetching track data");
        Track trackInfo;
        try {
            trackInfo = Zvuk.fetchTrackInfo(id);
        } catch (final Exception e) {
            System.err.println("Failed to fetch track data");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        System.out.println("Fetching album data");
        Album albumInfo = trackInfo.album();

        if (albumInfo.limitedData()) {
            try {
                albumInfo = Zvuk.fetchAlbumInfo(albumInfo.id());
            } catch (final Exception e) {
                System.err.println("Failed to fetch album info");
                System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
                return;
            }
        }

        System.out.println("Fetching album cover");
        Zvuk.Image image;

        try {
            image = Zvuk.downloadImage(Objects.notNull(albumInfo.coverSrc()).replace("{size}", "1000"));
        } catch (final Exception e) {
            System.err.println("Failed to fetch album cover");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        System.out.println("Writing tags");
        Mp3File mp3File;

        try {
            mp3File = new Mp3File(path.toFile());
        } catch (final Exception e) {
            System.err.println("Failed to open mp3 file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        final ID3v23Tag tag = new ID3v23Tag();

        final String trackTag = FORMAT_TO_2_DECIMALS.format(Objects.notNull(trackInfo.position()).intValue());
        tag.setTrack(trackTag);
        tag.setArtist(Artist.toString(trackInfo.artists()));
        tag.setTitle(trackInfo.title());
        tag.setAlbum(albumInfo.title());
        tag.setYear(Objects.notNull(albumInfo.date()).substring(0, 4));
        tag.setAlbumArtist(Artist.toString(albumInfo.artists()));
        tag.setAlbumImage(image.bytes(), image.mimeType());

        mp3File.setId3v2Tag(tag);

        System.out.println("Writing final file");
        try {
            mp3File.save(String.format("%s - %s.mp3", trackInfo, trackInfo.title()));
        } catch (final Exception e) {
            System.err.println("Failed to save mp3 file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
        }
    }

}
