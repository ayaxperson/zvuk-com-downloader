package io.github.ayaxperson.zvukcomdownloader.api;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Album(
        @NotNull String id,
        @NotNull String title,
        @Nullable String coverSrc,
        @Nullable String[] genres,
        @Nullable String date,
        @Nullable Artist[] artists
) {

    public static Album build(final JSONObject jsonObject) {
        if (!jsonObject.containsKey("id"))
            throw new IllegalArgumentException("JSON missing required element: id");

        final String id = jsonObject.getString("id");

        if (!jsonObject.containsKey("title"))
            throw new IllegalArgumentException("JSON missing required element: title;");

        final String title = jsonObject.getString("title");

        String coverSrc = null;

        if (jsonObject.containsKey("image")) {
            final JSONObject imageObject = jsonObject.getJSONObject("image");

            if (imageObject.containsKey("src"))
                coverSrc = imageObject.getString("src");
        }

        String[] genres = null;

        if (jsonObject.containsKey("genres")) {
            final JSONArray genresJsonArray = jsonObject.getJSONArray("genres");
            final int genresSize = genresJsonArray.size();
            genres = new String[genresSize];

            for (int i = 0; i < genresSize; i++) {
                genres[i] = genresJsonArray.getJSONObject(i).getString("name");
            }
        }

        String date = null;

        if (jsonObject.containsKey("date")) {
            date = jsonObject.getString("date");
        }

        Artist[] artists = null;

        if (jsonObject.containsKey("artists")) {
            final JSONArray artistsJsonArray = jsonObject.getJSONArray("artists");
            final int artistsSize = artistsJsonArray.size();
            artists = new Artist[artistsSize];

            for (int i = 0; i < artistsSize; i++) {
                artists[i] = Artist.build(artistsJsonArray.getJSONObject(i));
            }
        }

        if (jsonObject.containsKey("tracks")) {
            final JSONArray tracks = jsonObject.getJSONArray("tracks");

            for (int i = 0; i < tracks.size(); i++) {
                final JSONObject trackObject = (JSONObject) tracks.get(i);

                if (trackObject.containsKey("id")) {
                    final String trackId = trackObject.getString("id");
                    Zvuk.trackIndexMap.put(trackId, i + 1);
                }
            }
        }

        return new Album(id, title, coverSrc, genres, date, artists);
    }

    public boolean limitedData() {
        return genres == null || coverSrc == null || date == null || artists == null;
    }

}
