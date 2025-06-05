package io.github.ayaxperson.zvukcomdownloader.api;

import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;

public record Artist(
        @NotNull String id,
        @NotNull String name,
        @NotNull String cover
) {

    public static Artist build(final JSONObject jsonObject) {
        if (!jsonObject.containsKey("id"))
            throw new IllegalArgumentException("JSON missing required element: id");

        final String id = jsonObject.getString("id");

        String title;

        if (jsonObject.containsKey("title")) {
            title = jsonObject.getString("title");
        } else if (jsonObject.containsKey("name")) {
            title = jsonObject.getString("name");
        } else {
            throw new IllegalArgumentException("JSON missing required element: name");
        }

        String coverSrc;

        if (jsonObject.containsKey("image")) {
            final JSONObject imageObject = jsonObject.getJSONObject("image");

            if (!imageObject.containsKey("src"))
                throw new IllegalArgumentException("JSON missing required element: cover");

            coverSrc = imageObject.getString("src");
        } else if (jsonObject.containsKey("cover")) {
            coverSrc = jsonObject.getString("cover");
        } else {
            throw new IllegalArgumentException("JSON missing required element: cover");
        }


        return new Artist(id, title, coverSrc);
    }

    public static String toString(final Artist[] artists) {
        final StringBuilder trackArtistsTagBuilder = new StringBuilder();
        for (int i = 0; i < artists.length; i++) {
            final Artist artist = artists[i];
            if (i > 0)
                trackArtistsTagBuilder.append("/ ").append(artist.name());
            else
                trackArtistsTagBuilder.append(artist.name());
        }
        return trackArtistsTagBuilder.toString();
    }

}
