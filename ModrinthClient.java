package com.modhub.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Minimal Modrinth API v2 client. Only fetches public data over HTTPS.
 * Downloads are checked against Modrinth's SHA-1, restricted to .jar files,
 * and never overwrite an existing file. Downloaded files are never executed.
 */
public class ModrinthClient {
    public static final String GAME_VERSION = "1.21.4";
    private static final String API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "ModHub/1.0.0 (Minecraft 1.21.4 Fabric client mod)";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Hit(String id, String title, String author, String description, int downloads) {}

    public List<Hit> search(String query) throws Exception {
        String facets = "[[\"project_type:mod\"],[\"versions:" + GAME_VERSION + "\"],[\"categories:fabric\"]]";
        String url = API + "/search?limit=8&query=" + enc(query) + "&facets=" + enc(facets);
        JsonArray hits = get(url).getAsJsonObject().getAsJsonArray("hits");

        List<Hit> out = new ArrayList<>();
        for (JsonElement el : hits) {
            JsonObject h = el.getAsJsonObject();
            out.add(new Hit(
                    h.get("project_id").getAsString(),
                    h.get("title").getAsString(),
                    h.get("author").getAsString(),
                    h.get("description").getAsString(),
                    h.get("downloads").getAsInt()));
        }
        return out;
    }

    public Path download(Hit hit, Path modsDir) throws Exception {
        String versionsUrl = API + "/project/" + hit.id()
                + "/version?loaders=" + enc("[\"fabric\"]")
                + "&game_versions=" + enc("[\"" + GAME_VERSION + "\"]");
        JsonArray versions = get(versionsUrl).getAsJsonArray();
        if (versions.isEmpty()) {
            throw new IllegalStateException("No Fabric build for " + GAME_VERSION);
        }

        JsonObject version = versions.get(0).getAsJsonObject();
        JsonObject file = null;
        for (JsonElement el : version.getAsJsonArray("files")) {
            JsonObject f = el.getAsJsonObject();
            if (file == null) file = f;
            if (f.get("primary").getAsBoolean()) {
                file = f;
                break;
            }
        }
        if (file == null) {
            throw new IllegalStateException("No downloadable file found");
        }

        // getFileName() strips any path segments, so a hostile filename cannot escape the mods folder.
        String filename = Path.of(file.get("filename").getAsString()).getFileName().toString();
        if (!filename.endsWith(".jar")) {
            throw new IllegalStateException("Refusing non-jar file: " + filename);
        }
        Path target = modsDir.resolve(filename);
        if (Files.exists(target)) {
            throw new IllegalStateException(filename + " already exists; not overwritten");
        }

        String expectedSha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
        HttpRequest req = HttpRequest.newBuilder(URI.create(file.get("url").getAsString()))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Download failed with HTTP " + resp.statusCode());
        }

        Files.createDirectories(modsDir);
        Path tmp = modsDir.resolve(filename + ".part");
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new DigestInputStream(resp.body(), sha1);
             OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        String actual = HexFormat.of().formatHex(sha1.digest());
        if (!actual.equalsIgnoreCase(expectedSha1)) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("Checksum mismatch; download discarded");
        }
        return Files.move(tmp, target);
    }

    private JsonElement get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Modrinth returned HTTP " + resp.statusCode());
        }
        return JsonParser.parseString(resp.body());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
