package dev.qilletni.impl.debug;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.QilletniType;
import dev.qilletni.api.lang.types.SongType;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Track;
import dev.qilletni.impl.lang.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Display text and children for music types. Summaries never load music data; expanding a value may.
 */
final class MusicValues {

    private MusicValues() {}

    /**
     * @param accessor The suffix that gets this member from its parent in Qilletni, or {@code null}
     */
    record Member(String name, QilletniType value, String accessor) {}

    record MusicServices(MusicPopulator populator, EntityDefinitionManager entities) {}

    /**
     * @return The value's display text, or {@code null} if it isn't a music type
     */
    static String summary(QilletniType value) {
        return switch (value) {
            case SongType song -> songSummary(song);
            case AlbumType album -> albumSummary(album);
            case null, default -> null;
        };
    }

    static boolean hasChildren(QilletniType value) {
        return value instanceof SongType || value instanceof AlbumType;
    }

    /**
     * @return The value's children, or an empty list if it isn't a music type
     */
    static List<Member> members(QilletniType value, MusicServices services) {
        return switch (value) {
            case SongType song -> songMembers(song, services);
            case AlbumType album -> albumMembers(album, services);
            case null, default -> List.of();
        };
    }

    private static String songSummary(SongType song) {
        var track = peekTrack(song);
        if (track.isPresent()) {
            var artist = track.get().getArtist();
            return artist == null ? ValueFormatter.quote(track.get().getName())
                    : "%s by %s".formatted(ValueFormatter.quote(track.get().getName()), ValueFormatter.quote(artist.getName()));
        }

        return switch (song.getSongDefinition()) {
            case TITLE_ARTIST -> "%s by %s (not loaded)".formatted(ValueFormatter.quote(song.getSuppliedTitle()), ValueFormatter.quote(song.getSuppliedArtist()));
            case URL -> "%s (not loaded)".formatted(ValueFormatter.quote(song.getSuppliedUrl()));
            case PREPOPULATED -> "(not loaded)";
        };
    }

    private static String albumSummary(AlbumType albumType) {
        var album = peekAlbum(albumType);
        if (album.isPresent()) {
            var artist = album.get().getArtist();
            return artist == null ? ValueFormatter.quote(album.get().getName())
                    : "%s by %s".formatted(ValueFormatter.quote(album.get().getName()), ValueFormatter.quote(artist.getName()));
        }

        return switch (albumType.getAlbumDefinition()) {
            case TITLE_ARTIST -> "%s by %s (not loaded)".formatted(ValueFormatter.quote(albumType.getSuppliedTitle()), ValueFormatter.quote(albumType.getSuppliedArtist()));
            case URL -> "%s (not loaded)".formatted(ValueFormatter.quote(albumType.getSuppliedUrl()));
            case PREPOPULATED -> "(not loaded)";
        };
    }

    private static List<Member> songMembers(SongType song, MusicServices services) {
        Track track;
        try {
            if (peekTrack(song).isEmpty()) {
                services.populator().populateSong(song);
            }

            track = song.getTrack();
        } catch (RuntimeException e) {
            var members = new ArrayList<>(suppliedSongMembers(song));
            members.add(new Member("error", new StringTypeImpl(ErrorMessages.of(e)), null));
            return members;
        }

        var members = new ArrayList<Member>();
        members.add(new Member("title", new StringTypeImpl(track.getName()), ".getName()"));
        members.addAll(artistMembers(song, track, services.entities()));
        members.add(new Member("album", song.getAlbum(), ".getAlbum()"));
        members.add(new Member("duration", new IntTypeImpl(track.getDuration()), ".getDuration()"));
        members.add(new Member("id", new StringTypeImpl(track.getId()), ".getId()"));

        if (song.getSuppliedUrl() != null) {
            members.add(new Member("url", new StringTypeImpl(song.getSuppliedUrl()), ".getUrl()"));
        }

        return members;
    }

    private static List<Member> albumMembers(AlbumType albumType, MusicServices services) {
        Album album;
        try {
            if (peekAlbum(albumType).isEmpty()) {
                services.populator().populateAlbum(albumType);
            }

            album = albumType.getAlbum();
        } catch (RuntimeException e) {
            var members = new ArrayList<>(suppliedAlbumMembers(albumType));
            members.add(new Member("error", new StringTypeImpl(ErrorMessages.of(e)), null));
            return members;
        }

        var members = new ArrayList<Member>();
        members.add(new Member("title", new StringTypeImpl(album.getName()), ".getTitle()"));
        members.addAll(artistMembers(albumType, album, services.entities()));
        members.add(new Member("id", new StringTypeImpl(album.getId()), ".getId()"));

        if (albumType.getSuppliedUrl() != null) {
            members.add(new Member("url", new StringTypeImpl(albumType.getSuppliedUrl()), ".getUrl()"));
        }

        return members;
    }

    private static List<Member> artistMembers(SongType song, Track track, EntityDefinitionManager entities) {
        try {
            return List.of(new Member("artist", song.getArtist(entities), ".getArtist()"),
                    new Member("artists", song.getArtists(entities), ".getAllArtists()"));
        } catch (RuntimeException e) {
            var names = track.getArtists().stream().map(artist -> (QilletniType) new StringTypeImpl(artist.getName())).toList();
            return List.of(new Member("artist", new StringTypeImpl(track.getArtist().getName()), null),
                    new Member("artists", new ListTypeImpl(QilletniTypeClass.STRING, names), null));
        }
    }

    private static List<Member> artistMembers(AlbumType albumType, Album album, EntityDefinitionManager entities) {
        try {
            return List.of(new Member("artist", albumType.getArtist(entities), ".getArtist()"),
                    new Member("artists", albumType.getArtists(entities), ".getAllArtists()"));
        } catch (RuntimeException e) {
            var names = album.getArtists().stream().map(artist -> (QilletniType) new StringTypeImpl(artist.getName())).toList();
            return List.of(new Member("artist", new StringTypeImpl(album.getArtist().getName()), null),
                    new Member("artists", new ListTypeImpl(QilletniTypeClass.STRING, names), null));
        }
    }

    private static List<Member> suppliedSongMembers(SongType song) {
        return switch (song.getSongDefinition()) {
            case TITLE_ARTIST -> List.of(new Member("title", new StringTypeImpl(song.getSuppliedTitle()), null),
                    new Member("artist", new StringTypeImpl(song.getSuppliedArtist()), null));
            case URL -> List.of(new Member("url", new StringTypeImpl(song.getSuppliedUrl()), ".getUrl()"));
            case PREPOPULATED -> List.of();
        };
    }

    private static List<Member> suppliedAlbumMembers(AlbumType albumType) {
        return switch (albumType.getAlbumDefinition()) {
            case TITLE_ARTIST -> List.of(new Member("title", new StringTypeImpl(albumType.getSuppliedTitle()), null),
                    new Member("artist", new StringTypeImpl(albumType.getSuppliedArtist()), null));
            case URL -> List.of(new Member("url", new StringTypeImpl(albumType.getSuppliedUrl()), ".getUrl()"));
            case PREPOPULATED -> List.of();
        };
    }

    private static Optional<Track> peekTrack(SongType song) {
        try {
            return song instanceof SongTypeImpl impl ? impl.peekTrack() : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Album> peekAlbum(AlbumType album) {
        try {
            return album instanceof AlbumTypeImpl impl ? impl.peekAlbum() : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
