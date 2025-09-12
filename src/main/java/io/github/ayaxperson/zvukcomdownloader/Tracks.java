package io.github.ayaxperson.zvukcomdownloader;

import com.mpatric.mp3agic.ID3v23Tag;
import com.mpatric.mp3agic.Mp3File;
import io.github.ayaxperson.zvukcomdownloader.api.Album;
import io.github.ayaxperson.zvukcomdownloader.api.Artist;
import io.github.ayaxperson.zvukcomdownloader.api.Track;
import io.github.ayaxperson.zvukcomdownloader.api.Zvuk;

import java.nio.file.Path;

public class Tracks {

    public static void fetchTrackDataAndWriteToNewMP3(final String logPrefix, final String apiVersion, final String id, final Path path) {
        System.out.printf("[%s] [%s] Fetching track data%n", logPrefix, id);
        Track trackInfo;
        try {
            trackInfo = Zvuk.fetchTrackInfo(id, apiVersion);
        } catch (final Exception e) {
            System.err.println("Failed to fetch track data");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        System.out.printf("[%s] [%s] Fetching album data%n", logPrefix, id);
        Album albumInfo = trackInfo.album();

        if (albumInfo.limitedData()) {
            try {
                albumInfo = Zvuk.fetchAlbumInfo(albumInfo.id(), apiVersion);
            } catch (final Exception e) {
                System.err.println("Failed to fetch album info");
                System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
                return;
            }
        }

        System.out.printf("[%s] [%s] Fetching album cover%n", logPrefix, id);
        Zvuk.Image image;

        try {
            image = Zvuk.downloadImage(Utils.notNull(albumInfo.coverSrc()).replace("{size}", "1000"));
        } catch (final Exception e) {
            System.err.println("Failed to fetch album cover");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        System.out.printf("[%s] [%s] Writing tags%n", logPrefix, id);
        Mp3File mp3File;

        try {
            mp3File = new Mp3File(path.toFile());
        } catch (final Exception e) {
            System.err.println("Failed to open mp3 file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
            return;
        }

        final ID3v23Tag tag = new ID3v23Tag();

        final String trackTag = String.format("%02d", Utils.notNull(trackInfo.position()));
        tag.setTrack(trackTag);
        tag.setArtist(Artist.toString(trackInfo.artists()));
        tag.setTitle(trackInfo.title());
        tag.setAlbum(albumInfo.title());
        tag.setYear(Utils.notNull(albumInfo.date()).substring(0, 4));
        tag.setAlbumArtist(Artist.toString(albumInfo.artists()));
        tag.setAlbumImage(image.bytes(), image.mimeType());

        mp3File.setId3v2Tag(tag);

        System.out.printf("[%s] [%s] Writing final file%n", logPrefix, id);
        try {
            mp3File.save(String.format("%s - %s.mp3", trackTag, trackInfo.title()));
        } catch (final Exception e) {
            System.err.println("Failed to save mp3 file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
        }
    }

}
