package dev.qilletni.api.lib.qll.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.qilletni.api.lib.qll.ComparableVersion;

import java.io.IOException;
import java.util.Optional;

/**
 * Gson TypeAdapter for {@link ComparableVersion} that serializes/deserializes as a string.
 * Examples: "^1.2.3", "~2.0.1", "3.0.1"
 */
public class ComparableVersionTypeAdapter extends TypeAdapter<ComparableVersion> {

    @Override
    public void write(JsonWriter out, ComparableVersion value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        var versionString = new StringBuilder();

        // Add range specifier prefix if not EXACT
        char specifier = value.getRangeSpecifier().getSpecifier();
        if (specifier != '\0') {
            versionString.append(specifier);
        }

        // Add version numbers
        versionString.append(value.major())
                .append('.')
                .append(value.minor())
                .append('.')
                .append(value.patch())
                .append(value.isSnapshot() ? "-SNAPSHOT" : "");

        out.value(versionString.toString());
    }

    @Override
    public ComparableVersion read(JsonReader in) throws IOException {
        String versionString = in.nextString();

        Optional<ComparableVersion> parsedVersion = ComparableVersion.parseComparableVersionString(versionString);

        if (parsedVersion.isEmpty()) {
            throw new IOException("Invalid version string: " + versionString);
        }

        return parsedVersion.get();
    }
}
