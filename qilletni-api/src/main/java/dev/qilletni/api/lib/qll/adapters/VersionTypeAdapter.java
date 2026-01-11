package dev.qilletni.api.lib.qll.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.qilletni.api.lib.qll.Version;

import java.io.IOException;
import java.util.Optional;

/**
 * Gson TypeAdapter for {@link Version} that serializes/deserializes as a string.
 * Example: "3.0.1"
 */
public class VersionTypeAdapter extends TypeAdapter<Version> {

    @Override
    public void write(JsonWriter out, Version value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        // Add version numbers
        var versionString = String.valueOf(value.major()) +
                '.' +
                value.minor() +
                '.' +
                value.patch() +
                (value.isSnapshot() ? "-SNAPSHOT" : "");

        out.value(versionString);
    }

    @Override
    public Version read(JsonReader in) throws IOException {
        String versionString = in.nextString();

        Optional<Version> parsedVersion = Version.parseVersionString(versionString);

        if (parsedVersion.isEmpty()) {
            throw new IOException("Invalid version string: " + versionString);
        }

        return parsedVersion.get();
    }
}
