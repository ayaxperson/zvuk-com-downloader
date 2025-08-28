package io.github.ayaxperson.zvukcomdownloader.api;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;

import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Zvuk {

    private static final String DOWNLOAD_TRACK_TEMPLATE = "{\"query\":\"query getStream($ids: [ID!]!, $quality: String, $encodeType: String, $includeFlacDrm: Boolean!) {\\n  mediaContents(ids: $ids, quality: $quality, encodeType: $encodeType) {\\n    ... on Track {\\n      __typename\\n      stream {\\n        expire\\n        high\\n        mid\\n        flacdrm @include(if: $includeFlacDrm)\\n      }\\n    }\\n    ... on Episode {\\n      __typename\\n      stream {\\n        expire\\n        mid\\n      }\\n    }\\n    ... on Chapter {\\n      __typename\\n      stream {\\n        expire\\n        mid\\n      }\\n    }\\n  }\\n}\",\"variables\":{\"ids\":[%s],\"quality\":\"hq\",\"encodeType\":\"wv\",\"includeFlacDrm\":false},\"operationName\":\"getStream\"}";
    private static final String FETCH_TRACKS_FROM_PROFILE_TEMPLATE = "{\"query\":\"query getArtistCursorPopularTracks($ids: [ID!]!, $limit: Int!, $cursor: String) {\\n  getArtists(ids: $ids) {\\n    title\\n    getCursorPopularTracks(cursor: $cursor, limit: $limit) {\\n      page_info {\\n        hasNextPage\\n        endCursor\\n      }\\n      tracks {\\n        ...PlayerTrackData\\n      }\\n    }\\n  }\\n}\\n\\nfragment PlayerTrackData on Track {\\n  id\\n  title\\n  lyrics\\n  hasFlac\\n  duration\\n  explicit\\n  availability\\n  artistTemplate\\n  childParam\\n  mark\\n  artists {\\n    id\\n    title\\n    image {\\n      src\\n      palette\\n    }\\n    mark\\n    collectionItemData {\\n      itemStatus\\n    }\\n  }\\n  release {\\n    id\\n    title\\n    image {\\n      src\\n      palette\\n    }\\n  }\\n  collectionItemData {\\n    itemStatus\\n  }\\n  zchan\\n  __typename\\n}\",\"variables\":{\"ids\":[\"%s\"],\"limit\":%d,\"cursor\":\"%s\"},\"operationName\":\"getArtistCursorPopularTracks\"}";

    private static final String GRAPHQL_ENDPOINT_URL = "https://zvuk.com/api/v1/graphql";
    private static final String TRACK_INFO_URL = "https://zvuk.com/desktop-data/_next/data/v3.3.1/track/%s.json";
    private static final String ALBUM_INFO_URL = "https://zvuk.com/desktop-data/_next/data/v3.3.1/release/%s.json";

    public static final Map<String, Integer> trackIndexMap = new HashMap<>();

    private static final Header[] DEFAULT_HEADERS = new Header[] {
            new BasicHeader("accept", "application/graphql-response+json, application/json"),
            new BasicHeader("accept-language", "en-US,en;q=0.9"),
            new BasicHeader("content-type", "application/json"),
            new BasicHeader("priority", "u=1, i"),
    };

    private static final String[] USER_AGENTS = new String[] {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.3124.85",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; Xbox; Xbox One) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edge/44.18363.8131",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 OPR/118.0.0.0",
            "Mozilla/5.0 (Windows NT 10.0; WOW64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 OPR/118.0.0.0"
    };

    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

    public static Album fetchAlbumInfo(final String id) throws IOException {
        final HttpGet request = new HttpGet(String.format(ALBUM_INFO_URL, id));
        request.setHeaders(getHeaders());
        return HTTP_CLIENT.execute(request, new FetchAlbumInfoResponseHandler());
    }

    public static class FetchAlbumInfoResponseHandler implements HttpClientResponseHandler<Album> {

        @Override
        public Album handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int code = response.getCode();
            final HttpEntity entity = response.getEntity();

            if (code != 200 || entity == null) {
                if (code == 418) {
                    throw new APIException.DetectedBotException();
                } else {
                    if (entity != null) {
                        throw new APIException(String.format("Unhandled error in response: %d : %s", code, EntityUtils.toString(entity)));
                    } else {
                        throw new APIException(String.format("Unhandled error in response: %d", code));
                    }
                }
            }

            final String jsonSrc = EntityUtils.toString(entity);
            final JSONObject jsonObject = JSON.parseObject(jsonSrc);

            if (!jsonObject.containsKey("pageProps"))
                throw new IllegalArgumentException("JSON missing artist");

            final JSONObject pageProps = jsonObject.getJSONObject("pageProps");

            if (!pageProps.containsKey("hydrationData"))
                throw new IllegalArgumentException("JSON missing required element: hydrationData");

            final JSONObject hydrationData = pageProps.getJSONObject("hydrationData");

            if (!hydrationData.containsKey("headerReleaseWidget"))
                throw new IllegalArgumentException("JSON missing required element: headerReleaseWidget");

            final JSONObject headerReleaseWidget = hydrationData.getJSONObject("headerReleaseWidget");

            if (!headerReleaseWidget.containsKey("release"))
                throw new IllegalArgumentException("JSON missing required element: release");


            final JSONObject release = headerReleaseWidget.getJSONObject("release");

            return Album.build(release);
        }
    }

    public static Track fetchTrackInfo(final String id) throws IOException {
        final HttpGet request = new HttpGet(String.format(TRACK_INFO_URL, id));
        request.setHeaders(getHeaders());
        return HTTP_CLIENT.execute(request, new FetchTrackInfoResponseHandler());
    }

    public static class FetchTrackInfoResponseHandler implements HttpClientResponseHandler<Track> {

        @Override
        public Track handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int code = response.getCode();
            final HttpEntity entity = response.getEntity();

            if (code != 200 || entity == null) {
                if (code == 418) {
                    throw new APIException.DetectedBotException();
                } else {
                    if (entity != null) {
                        throw new APIException(String.format("Unhandled error in response: %d : %s", code, EntityUtils.toString(entity)));
                    } else {
                        throw new APIException(String.format("Unhandled error in response: %d", code));
                    }
                }
            }

            final String jsonSrc = EntityUtils.toString(entity);
            final JSONObject jsonObject = JSON.parseObject(jsonSrc);

            if (!jsonObject.containsKey("pageProps"))
                throw new IllegalArgumentException("JSON missing artist");

            final JSONObject pageProps = jsonObject.getJSONObject("pageProps");

            if (!pageProps.containsKey("hydrationData"))
                throw new IllegalArgumentException("JSON missing required element: hydrationData");

            final JSONObject hydrationData = pageProps.getJSONObject("hydrationData");

            if (!hydrationData.containsKey("headerTrack"))
                throw new IllegalArgumentException("JSON missing required element: headerTrack");

            final JSONObject headerTrack = hydrationData.getJSONObject("headerTrack");

            if (!headerTrack.containsKey("trackInfo"))
                throw new IllegalArgumentException("JSON missing required element: trackInfo");

            final JSONObject trackInfo = headerTrack.getJSONObject("trackInfo");

            if (!trackInfo.containsKey("track"))
                throw new IllegalArgumentException("JSON missing required element: track");

            final JSONObject track =  trackInfo.getJSONObject("track");

            return Track.build(track);
        }
    }

    public static Track[] fetchTracksFromAlbum(final String albumId) throws IOException {
        final Album album;

        try {
            album = fetchAlbumInfo(albumId);
        } catch (final Exception e) {
            throw new IOException("Failed to fetch album info", e);
        }

        final ArrayList<Track> tracks = new ArrayList<>(); // Using a list instead of a hard-set array in case some of the track ids are null for some reason
        for (final String trackId : album.trackIds()) {
            if (trackId == null)
                continue;

            final Track track;

            try {
                track = fetchTrackInfo(trackId);
            } catch (final Exception e) {
                throw new IOException(String.format("Failed to fetch track %s", trackId), e);
            }

            tracks.add(track);
        }
        return tracks.toArray(new Track[0]);
    }

    public static Track[] fetchTracksFromProfile(final String profileId, final String authToken) throws IOException {
        final HttpPost request = new HttpPost(GRAPHQL_ENDPOINT_URL);
        request.setHeaders(getHeaders(authToken));

        final List<Track> tracks = new ArrayList<>();

        boolean finished = false;
        String endCursor = null;

        while (!finished) {
            request.setEntity(new StringEntity(String.format(FETCH_TRACKS_FROM_PROFILE_TEMPLATE, profileId, 25, endCursor == null ? "" : endCursor)));
            final FetchTracksFromProfileResponse fetchTracksFromProfileResponse = HTTP_CLIENT.execute(request, new FetchTracksFromProfileResponseHandler());
            endCursor = fetchTracksFromProfileResponse.endCursor;
            if (!fetchTracksFromProfileResponse.hasNextPage)
                finished = true;

            tracks.addAll(Arrays.asList(fetchTracksFromProfileResponse.tracks));
        }

        return tracks.toArray(new Track[0]);
    }

    public record FetchTracksFromProfileResponse(
            Track[] tracks,
            boolean hasNextPage,
            String endCursor
    ) { }

    private static class FetchTracksFromProfileResponseHandler implements HttpClientResponseHandler<FetchTracksFromProfileResponse> {

        @Override
        public FetchTracksFromProfileResponse handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int code = response.getCode();
            final HttpEntity entity = response.getEntity();

            if (code != 200 || entity == null) {
                if (code == 418) {
                    throw new APIException.DetectedBotException();
                } else {
                    if (entity != null) {
                        throw new APIException(String.format("Unhandled error in response: %d : %s", code, EntityUtils.toString(entity)));
                    } else {
                        throw new APIException(String.format("Unhandled error in response: %d", code));
                    }
                }
            }

            final String jsonSrc = EntityUtils.toString(entity);
            final JSONObject jsonObject = JSON.parseObject(jsonSrc);

            if (!jsonObject.containsKey("data"))
                throw new IllegalArgumentException("JSON missing artist");

            if (!jsonObject.getJSONObject("data").containsKey("getArtists"))
                throw new IllegalArgumentException("JSON missing required element: getArtists");

            final JSONArray artists = jsonObject.getJSONObject("data").getJSONArray("getArtists");

            if (artists.isEmpty())
                throw new IllegalArgumentException("JSON missing required element: artists array entries");

            final JSONObject artist = (JSONObject) artists.getFirst();

            if (!artist.containsKey("getCursorPopularTracks"))
                throw new IllegalArgumentException("JSON missing required element: getCursorPopularTracks");

            if (!artist.getJSONObject("getCursorPopularTracks").containsKey("page_info"))
                throw new IllegalArgumentException("JSON missing required element: page_info");

            final JSONObject main = artist.getJSONObject("getCursorPopularTracks");

            final JSONObject pageInfo = main.getJSONObject("page_info");
            final boolean hasNextPage = pageInfo.getBoolean("hasNextPage");
            final String endCursor = pageInfo.getString("endCursor");

            final JSONArray tracksJsonArray = main.getJSONArray("tracks");

            final Track[] tracksArray = new Track[tracksJsonArray.size()];

            for (int i = 0; i < tracksJsonArray.size(); i++) {
                tracksArray[i] = Track.build((JSONObject) tracksJsonArray.get(i));
            }

            return new FetchTracksFromProfileResponse(tracksArray, hasNextPage, endCursor);
        }
    }

    public static void downloadTracks(final String authToken, final Map<String, Path> trackPathMap) throws IOException {
        final HttpPost request = new HttpPost(GRAPHQL_ENDPOINT_URL);
        request.setEntity(new StringEntity(String.format(DOWNLOAD_TRACK_TEMPLATE, stringsToJsonArray(trackPathMap.keySet().iterator()))));
        request.setHeaders(getHeaders(authToken));

        final String[] tracks = HTTP_CLIENT.execute(request, new FetchTracksURLResponseHandler());

        for (final String trackUrl : tracks) {
            final int idStartIndex = trackUrl.indexOf("/track/") + "/track/".length();
            final int idEndIndex = trackUrl.indexOf("/stream?");

            final String id = trackUrl.substring(idStartIndex, idEndIndex);
            final Path path = trackPathMap.get(id);

            HTTP_CLIENT.execute(new HttpGet(trackUrl), new FileResponseHandler(path));
        }
    }

    public static void downloadTrack(final String authToken, final String trackId, final Path path) throws IOException {
        if (Files.exists(path))
            throw new IOException("File already exists!");

        final HttpPost request = new HttpPost(GRAPHQL_ENDPOINT_URL);
        request.setEntity(new StringEntity(String.format(DOWNLOAD_TRACK_TEMPLATE, String.format("\"%s\"", trackId))));
        request.setHeaders(getHeaders(authToken));
        final String downloadUrl = HTTP_CLIENT.execute(request, new FetchTrackURLResponseHandler());
        HTTP_CLIENT.execute(new HttpGet(downloadUrl), new FileResponseHandler(path));
    }

    private record FileResponseHandler(Path path) implements HttpClientResponseHandler<Long> {

        @Override
        public Long handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int code = response.getCode();
            final HttpEntity entity = response.getEntity();

            if (code != 200 || entity == null) {
                if (code == 418) {
                    throw new APIException.DetectedBotException();
                } else {
                    if (entity != null) {
                        throw new APIException(String.format("Unhandled error in response: %d : %s", code, EntityUtils.toString(entity)));
                    } else {
                        throw new APIException(String.format("Unhandled error in response: %d", code));
                    }
                }
            }

            try (final FileOutputStream fos = new FileOutputStream(path.toFile())) {
                entity.writeTo(fos);
            }

            return Files.size(path);
        }
    }

    private static class FetchTrackURLResponseHandler implements HttpClientResponseHandler<String> {

        @Override
        public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int code = response.getCode();
            final HttpEntity entity = response.getEntity();

            if (code != 200 || entity == null) {
                if (code == 418) {
                    throw new APIException.DetectedBotException();
                } else {
                    if (entity != null) {
                        throw new APIException(String.format("Unhandled error in response: %d : %s", code, EntityUtils.toString(entity)));
                    } else {
                        throw new APIException(String.format("Unhandled error in response: %d", code));
                    }
                }
            }

            final String responseAsString = EntityUtils.toString(entity);

            if (responseAsString.contains("<html>"))
                throw new APIException.DetectedBotException();

            final int index = responseAsString.indexOf("\"mid\":\"");

            if (index == -1)
                throw new APIException(String.format("Response does not contain url to track %s", responseAsString));

            final char[] responseAsCharArray = responseAsString.toCharArray();

            final StringBuilder urlBuilder = new StringBuilder();

            for (int i = index + "\"mid\":\"".length(); i < responseAsCharArray.length; i++) {
                final char character = responseAsCharArray[i];

                if (character == '\"')
                    break;

                urlBuilder.append(character);
            }

            return urlBuilder.toString();
        }
    }

    private static class FetchTracksURLResponseHandler implements HttpClientResponseHandler<String[]> {

        @Override
        public String[] handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
            final int code = response.getCode();
            final HttpEntity entity = response.getEntity();

            if (code != 200 || entity == null) {
                if (code == 418) {
                    throw new APIException.DetectedBotException();
                } else {
                    if (entity != null) {
                        throw new APIException(String.format("Unhandled error in response: %d : %s", code, EntityUtils.toString(entity)));
                    } else {
                        throw new APIException(String.format("Unhandled error in response: %d", code));
                    }
                }
            }

            final String responseAsString = EntityUtils.toString(entity);
            final char[] responseAsCharArray = responseAsString.toCharArray();

            if (responseAsString.contains("<html>"))
                throw new APIException.DetectedBotException();

            final List<String> result = new ArrayList<>();

            int index = responseAsString.indexOf("\"mid\":\"");
            while (index >= 0) {
                final StringBuilder urlBuilder = new StringBuilder();

                for (int i = index + "\"mid\":\"".length(); i < responseAsCharArray.length; i++) {
                    final char character = responseAsCharArray[i];

                    if (character == '\"')
                        break;

                    urlBuilder.append(character);
                }

                result.add(urlBuilder.toString());

                index = responseAsString.indexOf("\"mid\":\"", index+1);
            }


            return result.toArray(new String[0]);
        }
    }

    public static Image downloadImage(final String url) throws IOException {
        final HttpGet request = new HttpGet(url);
        request.setHeaders(getHeaders());
        return HTTP_CLIENT.execute(request, new ImageResponseHandler());
    }

    public static class ImageResponseHandler implements HttpClientResponseHandler<Image> {

        @Override
        public Image handleResponse(ClassicHttpResponse response) throws IOException {
            final int status = response.getCode();

            if (status >= 200 && status < 300) {
                final HttpEntity entity = response.getEntity();

                if (entity != null) {
                    final byte[] imageBytes = EntityUtils.toByteArray(entity);
                    final ContentType contentType = ContentType.parse(entity.getContentType());
                    final String mimeType = contentType.getMimeType();
                    return new Image(imageBytes, mimeType);
                } else {
                    throw new IOException("Empty response entity");
                }
            } else {
                throw new IOException("Unexpected response status: " + status);
            }
        }

    }

    public record Image(byte[] bytes, String mimeType) { }

    public static void close() throws IOException {
        HTTP_CLIENT.close();
    }

    private static Header[] getHeaders() {
        final Header[] headers = new Header[DEFAULT_HEADERS.length + 1];

        int i = 0;
        while (i < DEFAULT_HEADERS.length) {
            headers[i] = DEFAULT_HEADERS[i];
            i++;
        }

        headers[i] = getUserAgentHeader();

        return headers;
    }

    private static Header[] getHeaders(final String authToken) {
        final Header[] headers = new Header[DEFAULT_HEADERS.length + 2];

        int i = 0;
        while (i < DEFAULT_HEADERS.length) {
            headers[i] = DEFAULT_HEADERS[i];
            i++;
        }

        headers[i] = getAuthTokenHeader(authToken);
        headers[i + 1] = getUserAgentHeader();

        return headers;
    }

    private static Header getAuthTokenHeader(final String authToken) {
        return new BasicHeader("x-auth-token", authToken);
    }

    private static Header getUserAgentHeader() {
        return new BasicHeader("User-Agent", USER_AGENTS[Math.toIntExact(Math.round(Math.random() * (USER_AGENTS.length - 1)))]);
    }

    private static String stringsToJsonArray(final Iterator<String> strings) {
        final StringBuilder builder = new StringBuilder("\"");

        int i = 0;
        while (strings.hasNext()) {
            final String s = strings.next();

            if (i == 0) {
                builder.append(s).append("\"");
            } else {
                builder.append(", \"").append(s).append("\"");
            }

            i++;
        }

        return builder.toString();
    }

    public static class APIException extends RuntimeException {

        public static class DetectedBotException extends APIException {

            public DetectedBotException() {
                super("You were detected as a bot!");
            }

        }

        public APIException(final String message) {
            super(message);
        }

    }

}
