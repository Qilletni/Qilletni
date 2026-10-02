package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.collection.CollectionLimit;
import dev.qilletni.api.lang.types.collection.CollectionLimitUnit;
import dev.qilletni.api.lang.types.collection.CollectionOrder;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.lang.types.weights.WeightUtils;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.orchestration.CollectionState;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.lang.types.weights.WeightUnit;
import dev.qilletni.api.music.play.PlayActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DefaultTrackOrchestrator implements TrackOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTrackOrchestrator.class);

    private final PlayActor playActor;
    private final MusicCache musicCache;

    public DefaultTrackOrchestrator(PlayActor playActor, MusicCache musicCache) {
        this.playActor = playActor;
        this.musicCache = musicCache;
    }

    @Override
    public void playTrack(Track track) {
        playActor.playTrack(track);
    }

    /*
     * Notes regarding weights:
     * If a collection is sequential, weights are not used
     * Total weight % must not go over 100%
     * Weight % has higher priority than multiplier, obviously
     * Weight % is total % played, NOT in addition. So, tracks with % weights are removed from shuffled queues
     * If playing while looping, it will wait until the track queue is empty, so % weights may make it go on for longer than intended
     */

    @Override
    public void playCollection(CollectionType collectionType, boolean loop) {
        LOGGER.debug("Play collection: {}", collectionType.getPlaylist().getTitle());

        conditionallyPlayCollection(collectionType, loop, track -> {
            playActor.playTrack(track).join();
        }, () -> true);
    }

    @Override
    public void playCollection(CollectionType collectionType, CollectionLimit collectionLimit) {
        LOGGER.debug("Play {} tracks from collection: {}", collectionType.getPlaylist().getTitle(), collectionLimit);

        if (collectionLimit.limitUnit() == CollectionLimitUnit.COUNT) {
            playCollectionCountLimited(collectionType, collectionLimit.limitCount(), true);
        } else {
            playCollectionTimeLimited(collectionType, calculateLimitMilliseconds(collectionLimit), true);
        }
    }

    @Override
    public Track getTrackFromCollection(CollectionState collectionState) {
        
        // if sequential, just get the next track from the cached track/index
        if (collectionState.getCollection().getOrder() == CollectionOrder.SEQUENTIAL) {
            int trackIndex = collectionState.getAndIncrementSequentialIndex();
            return collectionState.getTracks().get(trackIndex);
        }
        
        var atomicTrack = new AtomicReference<Track>();
        
        conditionallyPlayCollection(collectionState.getCollection(), true, atomicTrack::set, () -> atomicTrack.get() == null);

        return atomicTrack.get();
    }

    @Override
    public Track getTrackFromWeight(WeightsType weightsType) {
        var atomicTrack = new AtomicReference<Track>();

        conditionallyPlayWeightedTracks(Collections.emptyList(), weightsType, CollectionOrder.SHUFFLE, true, atomicTrack::set, () -> atomicTrack.get() == null);

        return atomicTrack.get();
    }

    private void playCollectionTimeLimited(CollectionType collectionType, long limitMilliseconds, boolean loop) {
        var totalTimePassed = new AtomicInteger();

        conditionallyPlayCollection(collectionType, loop, track -> {
                    totalTimePassed.addAndGet(track.getDuration());
                    playActor.playTrack(track).join();
                },
                () -> totalTimePassed.get() < limitMilliseconds);
    }

    private void playCollectionCountLimited(CollectionType collectionType, int count, boolean loop) {
        var totalTracksPlayed = new AtomicInteger();

        conditionallyPlayCollection(collectionType, loop, track -> {
                    totalTracksPlayed.incrementAndGet();
                    playActor.playTrack(track).join();
                },
                () -> totalTracksPlayed.get() < count);
    }

    private void conditionallyPlayCollection(CollectionType collectionType, boolean loop, Consumer<Track> playCallback, Supplier<Boolean> shouldPlayTrack) {
        var tracks = musicCache.getPlaylistTracks(collectionType.getPlaylist());

        conditionallyPlayWeightedTracks(tracks, collectionType.getWeights(), collectionType.getOrder(), loop, playCallback, shouldPlayTrack);
    }

    private void conditionallyPlayWeightedTracks(List<Track> tracks, WeightsType weights, CollectionOrder collectionOrder, boolean loop, Consumer<Track> playCallback, Supplier<Boolean> shouldPlayTrack) {
        if (collectionOrder == CollectionOrder.SEQUENTIAL) {
            for (Track track : tracks) {
                if (!shouldPlayTrack.get()) {
                    break;
                }

                playCallback.accept(track);
            }
            
            return;
        }

        WeightUtils.validateWeights(weights);
        var weightDispersion = WeightDispersion.initializeWeightDispersion(weights);

        tracks = prunePercentageWeightedTracks(tracks, weights);

        boolean initiallyEmpty = tracks.isEmpty();
        Queue<Track> trackQueue = applyWeights(tracks, weights);

        // If the weighted track chosen is this, grab one from the queue instead
        Track dontPlayNextTrack = null;
        WeightEntry dontPlayWeight = null;
        boolean isRetry = false;

        while ((initiallyEmpty || !trackQueue.isEmpty()) && (isRetry || shouldPlayTrack.get())) {
            isRetry = false;

            // Select a song from the weight. This may run this same method again if coming from another weight
            var weightedTrackContextOptional = weightDispersion.selectWeight();

            if (weightedTrackContextOptional.isPresent()) {
                var weightEntry = weightedTrackContextOptional.get();
                var weightedTrack = weightEntry.getTrack();

                // If the track or weight can't be repeated (and it was played last), don't play it
                if (weightedTrack.equals(dontPlayNextTrack) || weightEntry.equals(dontPlayWeight)) {
                    isRetry = true;
                    continue;
                }
                
                playCallback.accept(weightedTrack);

                // If only the current track can't be repeated, don't play the next track
                if (!weightEntry.getCanRepeatTrack()) {
                    dontPlayNextTrack = weightedTrack;
                }
                
                // If the weight can't be repeated, don't play the next track OR weight
                if (!weightEntry.getCanRepeatWeight()) {
                    dontPlayNextTrack = weightedTrack;
                    dontPlayWeight = weightEntry;
                }

                continue;
            }

            if (!initiallyEmpty) {
                var playingTrack = trackQueue.poll();
                playCallback.accept(playingTrack);
                dontPlayNextTrack = null;
                dontPlayWeight = null;

                if (trackQueue.isEmpty() && loop) {
                    trackQueue = applyWeights(tracks, weights);
                    trackQueue = ensureNoBeginningDuplicate(trackQueue, playingTrack);

                    LOGGER.debug("Reapplying weights to: {}", trackQueue);
                }
            }
        }
    }

    private Queue<Track> ensureNoBeginningDuplicate(Queue<Track> tracks, Track previouslyPlayed) {
        if (tracks.size() > 1 && tracks.peek() != null && tracks.peek().equals(previouslyPlayed)) {
            LOGGER.debug("Shuffled queue starts with previously played song, {}", previouslyPlayed.getName());
            var indexToPlace = ThreadLocalRandom.current().nextInt(0, tracks.size() - 2) + 1;

            LOGGER.debug("Will place at {}", indexToPlace);

            tracks.poll();

            var newTracks = new LinkedList<Track>();
            for (int i = 0; i < indexToPlace; i++) {
                newTracks.add(tracks.poll());
            }

            newTracks.add(previouslyPlayed);

            for (int i = 0; i < tracks.size(); i++) {
                newTracks.add(tracks.poll());
            }

            LOGGER.debug("New shuffled queue = {}", newTracks.stream().map(Track::getName).collect(Collectors.joining(", ")));

            return newTracks;
        }

        return tracks;
    }

    /**
     * Calculates the number of milliseconds the {@link CollectionLimit} represents. If the unit is of type
     * {@link CollectionLimitUnit#COUNT}, -1 is returned.
     *
     * @param collectionLimit The collection limit to calculate
     * @return The number of milliseconds
     */
    private long calculateLimitMilliseconds(CollectionLimit collectionLimit) {
        return collectionLimit.limitUnit().getTimeUnit().toMillis(collectionLimit.limitCount());
    }

    private List<Track> prunePercentageWeightedTracks(List<Track> tracks, WeightsType weights) {
        if (weights == null) {
            return tracks;
        }

        var removeTracks = weights.getWeightEntries().stream()
                .filter(entry -> entry.getWeightUnit() == WeightUnit.PERCENT)
                .filter(entry -> !entry.isInconsistent())
                .flatMap(entry -> entry.getAllTracks().stream())
                .toList();

        var trackCopy = new ArrayList<>(tracks);
        trackCopy.removeIf(removeTracks::contains);
        
        return trackCopy;
    }

    private Queue<Track> applyWeights(List<Track> tracks, WeightsType weights) {
        LinkedList<Track> trackQueue;
        
        if (weights != null && !tracks.isEmpty()) {
            record TrackMapWeight(Track track, WeightEntry weight) {}
            
            var trackWeightMap = weights.getWeightEntries().stream()
                    .filter(entry -> entry.getWeightUnit() == WeightUnit.MULTIPLIER)
                    .filter(entry -> !entry.isInconsistent())
                    .flatMap(entry -> entry.getAllTracks().stream().map(track -> new TrackMapWeight(track, entry)))
                    .collect(Collectors.toMap(TrackMapWeight::track, trackMapWeight -> (int) (trackMapWeight.weight.getWeightAmount() - 1)));

            trackQueue = tracks.stream()
                    .flatMap(track -> IntStream.range(0, trackWeightMap.getOrDefault(track, 0) + 1).mapToObj($ -> track))
                    .collect(Collectors.toCollection(LinkedList::new));
        } else {
            trackQueue = new LinkedList<>(tracks);
        }

        Collections.shuffle(trackQueue);

        return trackQueue;
    }
}
