package dev.qilletni.impl.debug;

import dev.qilletni.api.auth.ServiceProvider;
import dev.qilletni.api.lang.types.*;
import dev.qilletni.api.lang.types.entity.EntityDefinitionManager;
import dev.qilletni.api.lang.types.typeclass.QilletniTypeClass;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.music.Album;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Playlist;
import dev.qilletni.api.music.Track;
import dev.qilletni.impl.lang.types.*;
import dev.qilletni.impl.lang.types.weights.LazyWeightEntry;
import dev.qilletni.impl.lang.types.weights.WeightEntryImpl;
import dev.qilletni.impl.music.DummyPlaylist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Display text and children for music types. Summaries never load music data; expanding a value may.
 */
final class MusicValues {

    private MusicValues() {}

    /**
     * @param accessor The suffix that gets this member from its parent in Qilletni, or {@code null}
     * @param row      A row shown instead of {@code value}, or {@code null}
     */
    record Member(String name, QilletniType value, String accessor, MusicStop.Row row) {

        Member(String name, QilletniType value, String accessor) {
            this(name, value, accessor, null);
        }

        static Member row(MusicStop.Row row) {
            return new Member(row.name(), null, null, row);
        }
    }

    record MusicServices(MusicPopulator populator, EntityDefinitionManager entities) {}

    /**
     * @return The value's display text, or {@code null} if it isn't a music type
     */
    static String summary(QilletniType value) {
        return switch (value) {
            case SongType song -> songSummary(song);
            case AlbumType album -> albumSummary(album);
            case CollectionType collection -> collectionSummary(collection);
            case WeightsType weights -> weightsSummary(weights);
            case null, default -> null;
        };
    }

    static boolean hasChildren(QilletniType value) {
        return value instanceof SongType || value instanceof AlbumType || value instanceof CollectionType
                || value instanceof WeightsType weights && !weights.getWeightEntries().isEmpty();
    }

    /**
     * @return The value's children, or an empty list if it isn't a music type
     */
    static List<Member> members(QilletniType value, MusicServices services) {
        return switch (value) {
            case SongType song -> songMembers(song, services);
            case AlbumType album -> albumMembers(album, services);
            case CollectionType collection -> collectionMembers(collection, services);
            case WeightsType weights -> weightsMembers(weights, services);
            case null, default -> List.of();
        };
    }

    private static String songSummary(SongType song) {
        var track = peekTrack(song);
        if (track.isPresent()) {
            return trackText(track.get());
        }

        return switch (song.getSongDefinition()) {
            case TITLE_ARTIST -> "%s by %s (not loaded)".formatted(ValueFormatter.quote(song.getSuppliedTitle()), ValueFormatter.quote(song.getSuppliedArtist()));
            case URL -> "%s (not loaded)".formatted(ValueFormatter.quote(song.getSuppliedUrl()));
            case PREPOPULATED -> "(not loaded)";
        };
    }

    /**
     * @return e.g. {@code "Title" by "Artist"}
     */
    static String trackText(Track track) {
        var artist = track.getArtist();
        return artist == null ? ValueFormatter.quote(track.getName())
                : "%s by %s".formatted(ValueFormatter.quote(track.getName()), ValueFormatter.quote(artist.getName()));
    }

    private static String albumSummary(AlbumType albumType) {
        var album = peekAlbum(albumType);
        if (album.isPresent()) {
            var artist = album.get().getArtist();
            return artist == null ? ValueFormatter.quote(album.get().getName())
                    : "%s by %s".formatted(ValueFormatter.quote(album.get().getName()), ValueFormatter.quote(artist.getName()));
        }

        return switch (albumType.getAlbumDefinition()) {
            case TITLE_ARTIST -> "%s album by %s (not loaded)".formatted(ValueFormatter.quote(albumType.getSuppliedTitle()), ValueFormatter.quote(albumType.getSuppliedArtist()));
            case URL -> "%s (not loaded)".formatted(ValueFormatter.quote(albumType.getSuppliedUrl()));
            case PREPOPULATED -> "(not loaded)";
        };
    }

    private static String collectionSummary(CollectionType collectionType) {
        var playlist = peekPlaylist(collectionType);
        if (playlist.isPresent()) {
            return playlistSummary(playlist.get());
        }

        return switch (collectionType.getCollectionDefinition()) {
            case NAME_CREATOR -> "%s by %s (not loaded)".formatted(ValueFormatter.quote(collectionType.getSuppliedName()), ValueFormatter.quote(collectionType.getSuppliedCreator()));
            case URL -> "%s (not loaded)".formatted(ValueFormatter.quote(collectionType.getSuppliedUrl()));
            // Only loaded under a provider that has since been switched away from
            case PREPOPULATED, SONG_LIST -> peekAnyPlaylist(collectionType)
                    .map(entry -> entry.getValue() instanceof DummyPlaylist dummyPlaylist
                            ? "song[%d] (locally defined playlist, not loaded)".formatted(dummyPlaylist.getTrackCount())
                            : "%s (from %s, not loaded)".formatted(playlistSummary(entry.getValue()), entry.getKey().getName()))
                    .orElse("(not loaded)");
        };
    }

    private static String playlistSummary(Playlist playlist) {
        if (playlist instanceof DummyPlaylist dummyPlaylist) {
            return "song[%d] (locally defined playlist)".formatted(dummyPlaylist.getTrackCount());
        }

        var creator = playlist.getCreator();
        return creator == null ? ValueFormatter.quote(playlist.getTitle())
                : "%s by %s".formatted(ValueFormatter.quote(playlist.getTitle()), ValueFormatter.quote(creator.getName()));
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

    private static List<Member> collectionMembers(CollectionType collectionType, MusicServices services) {
        Playlist playlist;
        try {
            if (peekPlaylist(collectionType).isEmpty()) {
                services.populator().populateCollection(collectionType);
            }

            playlist = collectionType.getPlaylist();
        } catch (RuntimeException e) {
            var members = new ArrayList<>(suppliedCollectionMembers(collectionType));
            members.add(new Member("error", new StringTypeImpl(ErrorMessages.of(e)), null));
            return members;
        }

        var members = new ArrayList<Member>();
        if (!(playlist instanceof DummyPlaylist)) {
            members.add(new Member("name", new StringTypeImpl(playlist.getTitle()), ".getName()"));
            members.add(creatorMember(collectionType, playlist, services.entities()));
        }

        members.add(new Member("trackCount", new IntTypeImpl(playlist.getTrackCount()), ".getTrackCount()"));
        members.add(new Member("order", new StringTypeImpl(collectionType.getOrder().getOrderString()), null));

        if (collectionType.getWeights() != null) {
            members.add(new Member("weights", collectionType.getWeights(), null));
        }

        if (!(playlist instanceof DummyPlaylist)) {
            members.add(new Member("id", new StringTypeImpl(playlist.getId()), ".getId()"));
        }

        if (collectionType.getSuppliedUrl() != null) {
            members.add(new Member("url", new StringTypeImpl(collectionType.getSuppliedUrl()), ".getUrl()"));
        }

        return members;
    }

    private static Member creatorMember(CollectionType collectionType, Playlist playlist, EntityDefinitionManager entities) {
        try {
            return new Member("creator", collectionType.getCreator(entities), ".getCreator()");
        } catch (RuntimeException e) {
            var creator = playlist.getCreator();
            return new Member("creator", new StringTypeImpl(creator == null ? "" : creator.getName()), null);
        }
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

    private static List<Member> suppliedCollectionMembers(CollectionType collectionType) {
        return switch (collectionType.getCollectionDefinition()) {
            case NAME_CREATOR -> List.of(new Member("name", new StringTypeImpl(collectionType.getSuppliedName()), null),
                    new Member("creator", new StringTypeImpl(collectionType.getSuppliedCreator()), null));
            case URL -> List.of(new Member("url", new StringTypeImpl(collectionType.getSuppliedUrl()), ".getUrl()"));
            case PREPOPULATED, SONG_LIST -> List.of();
        };
    }

    private static String weightsSummary(WeightsType weights) {
        var count = weights.getWeightEntries().size();
        return "weights(%d %s)".formatted(count, count == 1 ? "entry" : "entries");
    }

    /**
     * One row per entry, named like its source, e.g. {@code [0] |!50%}, holding the value it picks tracks from.
     */
    private static List<Member> weightsMembers(WeightsType weights, MusicServices services) {
        var entries = weights.getWeightEntries();
        var members = new ArrayList<Member>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            members.add(entry instanceof LazyWeightEntry ? Member.row(entryRow(entryName(i, entry), entry)) : new Member(entryName(i, entry), entrySource(entry), null));
        }

        members.add(Member.row(chancesGroup(WeightChances.breakdown(weights))));
        return members;
    }

    /**
     * One row per path to a leaf with its combined chance, then the miss chance and multiplier entries.
     */
    private static MusicStop.Group chancesGroup(WeightChances.Tree tree) {
        var rows = new ArrayList<MusicStop.Row>();
        var paths = WeightChances.leafPaths(tree);
        var leaves = 0;

        for (var path : paths) {
            var leaf = path.getLast();
            var name = String.join(" → ", path.stream().map(WeightChances.Node::name).toList());

            if (leaf.kind().equals("multiplier")) {
                var text = path.size() == 1 ? "copies in the collection's queue, not rolled" : "not used in nested weights";
                rows.add(new MusicStop.Text(name, text));
                continue;
            }

            leaves++;
            var chance = WeightChances.percent(leaf.combined());
            var size = leaf.listSize();
            if (size > 0) {
                chance += " (%s songs, %s each)".formatted(WeightChances.count(size), WeightChances.percent(leaf.combined() / size));
            }

            var entryRow = leaf.source() == null ? new MusicStop.Text("entry", leaf.sourceText()) : new MusicStop.Value("entry", leaf.source());
            rows.add(new MusicStop.Group(name, chance, List.of(entryRow)));
        }

        if (tree.omitted() > 0) {
            rows.add(new MusicStop.Text("… %s more".formatted(WeightChances.count(tree.omitted())), WeightChances.percent(tree.omittedChance())));
        }

        if (tree.miss() > 0) {
            rows.add(new MusicStop.Text("miss", WeightChances.percent(tree.miss()) + " → the collection's own tracks"));
        }

        var pathCount = leaves + tree.omitted();
        var value = "%s %s".formatted(WeightChances.count(pathCount), pathCount == 1 ? "path" : "paths");
        if (tree.miss() > 0) {
            value += ", %s miss".formatted(WeightChances.percent(tree.miss()));
        }

        return new MusicStop.Group("chances", value, rows);
    }

    /**
     * @return The entry named like its source, e.g. {@code [0] |!50%}
     */
    static String entryName(int index, WeightEntry entry) {
        return "[%d] %s%s".formatted(index, weightPrefix(entry), weightAmount(entry));
    }

    /**
     * @return A row named {@code name} showing what the entry picks from: the call for a function entry, otherwise the value
     */
    static MusicStop.Row entryRow(String name, WeightEntry entry) {
        return entry instanceof LazyWeightEntry lazy ? new MusicStop.Text(name, lazy.getCallText()) : new MusicStop.Value(name, entrySource(entry));
    }

    /**
     * @return Display text of what the entry picks from: the call for a function entry, otherwise the value
     */
    static String entrySourceText(WeightEntry entry) {
        return entry instanceof LazyWeightEntry lazy ? lazy.getCallText() : ValueFormatter.value(entrySource(entry));
    }

    /**
     * @return The value the entry picks tracks from
     */
    static QilletniType entrySource(WeightEntry entry) {
        var source = entry instanceof WeightEntryImpl impl ? impl.getSourceValue() : null;
        return source == null ? new StringTypeImpl("function call") : source;
    }

    private static String weightPrefix(WeightEntry entry) {
        if (entry.getCanRepeatTrack()) {
            return "|!";
        }

        return entry.getCanRepeatWeight() ? "|" : "|~";
    }

    private static String weightAmount(WeightEntry entry) {
        var amount = entry.getWeightAmount();
        var number = amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
        return number + entry.getWeightUnit().getStringUnit();
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

    private static Optional<Map.Entry<ServiceProvider, Playlist>> peekAnyPlaylist(CollectionType collectionType) {
        try {
            return collectionType instanceof CollectionTypeImpl impl ? impl.peekAnyPlaylist() : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Playlist> peekPlaylist(CollectionType collectionType) {
        try {
            return collectionType instanceof CollectionTypeImpl impl ? impl.peekPlaylist() : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
