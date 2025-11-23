package dev.qilletni.api.lib.qll;

import java.util.Objects;
import java.util.Optional;

/**
 * A version of a Qilletni library.
 */
public class Version {
    
    private final int major;
    private final int minor;
    private final int patch;
    private final boolean snapshot;

    /**
     * Creates a new {@link Version} with the given major, minor, and patch versions.
     *
     * @param major    The major version
     * @param minor    The minor version
     * @param patch    The patch version
     * @param snapshot If the version is a snapshot
     */
    public Version(int major, int minor, int patch, boolean snapshot) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.snapshot = snapshot;
    }

    /**
     * Parses a {@link Version} from a string, such as {@code 1.0.0}.
     * 
     * @param versionString The version string to parse
     * @return The created {@link Version}, if the string was valid
     */
    public static Optional<Version> parseVersionString(String versionString) {
        var versionSplit = versionString.split("\\.");

        if (versionSplit.length != 3) {
            return Optional.empty();
        }

        var minorNumber = versionSplit[2];
        var snapshotVersion = minorNumber.endsWith("-SNAPSHOT");
        if (snapshotVersion) {
            minorNumber =  minorNumber.substring(0, minorNumber.length() - "-SNAPSHOT".length());
        }
        
        return Optional.of(new Version(Integer.parseInt(versionSplit[0]), Integer.parseInt(versionSplit[1]), Integer.parseInt(minorNumber), snapshotVersion));
    }

    /**
     * Gets the major version of the {@link Version}.
     * 
     * @return The major version
     */
    public int major() {
        return major;
    }

    /**
     * Gets the minor version of the {@link Version}.
     * 
     * @return The minor version
     */
    public int minor() {
        return minor;
    }

    /**
     * Gets the patch version of the {@link Version}.
     * 
     * @return The patch version
     */
    public int patch() {
        return patch;
    }

    public boolean isSnapshot() {
        return snapshot;
    }

    /**
     * Gets the version string of the {@link Version}, such as {@code 1.0.0}.
     * 
     * @return The version string
     */
    public String getVersionString() {
        return "%d.%d.%d%s".formatted(major, minor, patch, snapshot ? "-SNAPSHOT" : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Version) obj;
        return this.major == that.major &&
                this.minor == that.minor &&
                this.patch == that.patch &&
                this.snapshot == that.snapshot;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, snapshot);
    }

    @Override
    public String toString() {
        return "Version[" +
                "major=" + major + ", " +
                "minor=" + minor + ", " +
                "patch=" + patch + ", " +
                "snapshot=" + snapshot +
                ']';
    }

}
