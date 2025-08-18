package io.github.ayaxperson.zvukcomdownloader.api;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;

public record Track(
        @NotNull String id,
        @NotNull String title,
        @NotNull Album album,
        @NotNull Artist[] artists,
        @NotNull Integer position
    ) {

    public static Track build(final JSONObject jsonObject) {
        if (!jsonObject.containsKey("id"))
            throw new IllegalArgumentException("JSON missing required element: id");

        final String id = jsonObject.getString("id");

        if (!jsonObject.containsKey("title"))
            throw new IllegalArgumentException("JSON missing required element: title");

        final String title = jsonObject.getString("title");

        if (!jsonObject.containsKey("release"))
            throw new IllegalArgumentException("JSON missing required element: release");

        final JSONObject albumJsonObject = jsonObject.getJSONObject("release");
        Album album = Album.build(albumJsonObject);

        if (jsonObject.containsKey("coverSrc") && album.coverSrc() == null) {
            album = new Album(album.id(), album.title(), jsonObject.getString("coverSrc"), album.genres(), album.date(), album.artists(), album.trackIds());
        }

        if (!jsonObject.containsKey("artists"))
            throw new IllegalArgumentException("JSON missing required element: artists");

        final JSONArray artistsJsonArray = jsonObject.getJSONArray("artists");

        final Artist[] artistsArray = new Artist[artistsJsonArray.size()];

        for (int i = 0; i < artistsJsonArray.size(); i++) {
            artistsArray[i] = Artist.build((JSONObject) artistsJsonArray.get(i));
        }

        Integer position = null;

        if (jsonObject.containsKey("position"))
            position = jsonObject.getInteger("position");

        if (position == null) {
            if (Zvuk.trackIndexMap.containsKey(id)) {
                position = Zvuk.trackIndexMap.get(id);
            } else {
                try {
                    Zvuk.fetchAlbumInfo(album.id());
                } catch (final Exception ignored) { }
                position = Zvuk.trackIndexMap.getOrDefault(id, 1);
            }
        }

        return new Track(id, title, album, artistsArray, position);
    }

}
