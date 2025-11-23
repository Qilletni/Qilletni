package dev.qilletni.api.lib.qll;

import java.util.Arrays;
import java.util.Optional;

/**
 * A {@link Version} that may be compared with others, fitting a range.
 */
public class ComparableVersion extends Version implements Comparable<ComparableVersion> {

    private final RangeSpecifier rangeSpecifier;

    /**
     * Creates a new {@link ComparableVersion} with the given major, minor, and patch versions, and a range specifier.
     * 
     * @param major The major version
     * @param minor The minor version
     * @param patch The patch version
     * @param rangeSpecifier The range specifier for the version
     */
    public ComparableVersion(int major, int minor, int patch, boolean snapshot, RangeSpecifier rangeSpecifier) {
        super(major, minor, patch, snapshot);
        this.rangeSpecifier = rangeSpecifier;
    }

    /**
     * Creates a new {@link ComparableVersion} with the given {@link Version} and range specifier.
     * 
     * @param version The version to use, specifying the major, minor, and patch versions
     * @param rangeSpecifier The range specifier for the version
     */
    public ComparableVersion(Version version, RangeSpecifier rangeSpecifier) {
        this(version.major(), version.minor(), version.patch(), version.isSnapshot(), rangeSpecifier);
    }

    /**
     * Parses a {@link ComparableVersion} from a string, such as {@code ^1.0.0}. If the version doesn't contain a range
     * specifier, then {@link RangeSpecifier#EXACT} is set.
     * 
     * @param versionString The version string to parse
     * @return The created {@link ComparableVersion}, if the string was valid
     */
    public static Optional<ComparableVersion> parseComparableVersionString(String versionString) {
        if (versionString.isEmpty()) {
            return Optional.empty();
        }
        
        var rangeSpecifierOptional = RangeSpecifier.getRangeSpecifierFromString(versionString.charAt(0));
        RangeSpecifier rangeSpecifier = rangeSpecifierOptional.orElse(RangeSpecifier.EXACT);
        
        if (rangeSpecifierOptional.isPresent()) {
            versionString = versionString.substring(1);
        }

        return Version.parseVersionString(versionString)
                .map(version -> new ComparableVersion(version, rangeSpecifier));
    }

    /**
     * Gets the range specifier for this version.
     *
     * @return The range specifier
     */
    public RangeSpecifier getRangeSpecifier() {
        return rangeSpecifier;
    }

    /**
     * Checks if the current {@link ComparableVersion} permits the given {@link Version} to be used in the current
     * range.
     *
     * @param checkVersion The version to compare
     * @return If the version fits the bounds of the current {@link RangeSpecifier}
     */
    public boolean permitsVersion(Version checkVersion) {
        return rangeSpecifier.permitsVersion(this, checkVersion);
    }

    /**
     * Comparable method to allow comparison between versions using java Comparables
     * @param other the ComparableVersion to be compared.
     * @return the compare value between this version and the other ComparableVersion
     */
    @Override
    public int compareTo(ComparableVersion other) {
        if (this.major() != other.major()) {
            return Integer.compare(this.major(), other.major());
        }
        if (this.minor() != other.minor()) {
            return Integer.compare(this.minor(), other.minor());
        }
        return Integer.compare(this.patch(), other.patch());
    }

    /**
     * Specifies how a version may be matched with other versions.
     */
    public enum RangeSpecifier {
        /**
         * Permits newer minor and match versions, so the {@code x} of {@code 1.x.x}.
         */
        CARET('^') {
            @Override
            public boolean permitsVersion(Version version, Version checkVersion) {
                if (checkVersion.major() != version.major()) {
                    return false;
                }
                
                if (checkVersion.minor() >= version.minor()) {
                    return true;
                }
                
                return checkVersion.patch() >= version.patch();
            }
        },
        /**
         * Permits newer patch versions, so the {@code x} of {@code 1.0.x}.
         */
        TILDE('~') {
            @Override
            public boolean permitsVersion(Version version, Version checkVersion) {
                if (checkVersion.major() != version.major() || checkVersion.minor() != version.minor()) {
                    return false;
                }

                return checkVersion.patch() >= version.patch();
            }
        },
        /**
         * Requires the exact version specified.
         */
        EXACT('\0') {
            @Override
            public boolean permitsVersion(Version version, Version checkVersion) {
                return !(checkVersion.major() != version.major() || checkVersion.minor() != version.minor()
                        || checkVersion.patch() != version.patch());
            }
        };
        
        private final char specifier;

        RangeSpecifier(char specifier) {
            this.specifier = specifier;
        }

        public char getSpecifier() {
            return specifier;
        }

        public static Optional<RangeSpecifier> getRangeSpecifierFromString(char specifierChar) {
            return Arrays.stream(values())
                    .filter(rangeSpecifier -> rangeSpecifier.specifier == specifierChar)
                    .findFirst();
        }

        /**
         * Checks if {@code #version} {@link ComparableVersion} will permit the given {@link Version} to be used in
         * accordance to the version's range specifier.
         * 
         * @param version The version to check against
         * @param checkVersion The version to check matches {@code version}
         * @return If the version may be used to fulfill the 
         */
        public abstract boolean permitsVersion(Version version, Version checkVersion);
    }

    @Override
    public String toString() {
        return "ComparableVersion{" +
                "v" + major() + "." + minor() + "." + patch() + ", " +
                "rangeSpecifier=" + rangeSpecifier +
                '}';
    }
}
