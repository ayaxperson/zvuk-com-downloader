package io.github.ayaxperson.zvukcomdownloader.action;

import io.github.ayaxperson.zvukcomdownloader.Main;
import io.github.ayaxperson.zvukcomdownloader.Tracks;
import io.github.ayaxperson.zvukcomdownloader.Utils;
import io.github.ayaxperson.zvukcomdownloader.api.Track;
import io.github.ayaxperson.zvukcomdownloader.api.Zvuk;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record DownloadDiscography(String id, String authToken, String apiVersion) implements Runnable {

    public void run() {
        System.out.printf("[%s] Fetching tracks%n", id);
        Track[] tracks;

        try {
            tracks = Zvuk.fetchTracksFromProfile(id, authToken, apiVersion);
        } catch (final Exception e) {
            System.err.printf("[%s] Failed to fetch tracks%n", id);
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        final Map<String, Path> trackPathMap = new HashMap<>();

        for (int i = 0; i < tracks.length; i++) {
            final Track track = tracks[i];

            final Path tempPath = Path.of(String.format("temp-%s-%d-%s.mp3", id, i, Utils.getRandomString(10)));
            Main.TEMPORARY_FILES.add(tempPath);
            trackPathMap.put(track.id(), tempPath);
        }

        System.out.printf("[%s] Downloading tracks%n", id);
        try {
            Zvuk.downloadTracks(authToken, trackPathMap);
        } catch (final Exception e) {
            System.err.printf("[%s] Failed to download file%n", id);
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        for (final Track track : tracks) {
            // Not reusing the track object here is not a mistake as this track object does not have the necessary data
            Tracks.fetchTrackDataAndWriteToNewMP3(id, apiVersion, track.id(), trackPathMap.get(track.id()));
        }
    }

}
