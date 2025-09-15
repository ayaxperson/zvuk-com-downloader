package io.github.ayaxperson.zvukcomdownloader.action;

import io.github.ayaxperson.zvukcomdownloader.Main;
import io.github.ayaxperson.zvukcomdownloader.Tracks;
import io.github.ayaxperson.zvukcomdownloader.Utils;
import io.github.ayaxperson.zvukcomdownloader.api.Zvuk;

import java.nio.file.Path;

public record DownloadTrack(String id, String authToken, String apiVersion) implements Runnable {

    public void run() {
        System.out.printf("[%s] Downloading temp mp3%n", id);
        final Path tempPath = Path.of(String.format("temp-%s-%s.mp3", id, Utils.getRandomString(10)));
        Main.TEMPORARY_FILES.add(tempPath);
        try {
            Zvuk.downloadTrack(authToken, id, tempPath);
        } catch (final Exception e) {
            System.err.printf("[%s] Failed to download file%n", id);
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        Tracks.fetchTrackDataAndWriteToNewMP3(id, apiVersion, id, tempPath);
    }

}
