package dev.qilletni.impl.music.orchestration;

import dev.qilletni.api.lang.types.AlbumType;
import dev.qilletni.api.lang.types.CollectionType;
import dev.qilletni.api.lang.types.OrderableTracksType;
import dev.qilletni.api.lang.types.WeightsType;
import dev.qilletni.api.lang.types.collection.CollectionLimit;
import dev.qilletni.api.lang.types.collection.CollectionOrder;
import dev.qilletni.api.lang.types.weights.WeightEntry;
import dev.qilletni.api.lang.types.weights.WeightUnit;
import dev.qilletni.api.lang.types.weights.WeightUtils;
import dev.qilletni.api.music.MusicCache;
import dev.qilletni.api.music.MusicPopulator;
import dev.qilletni.api.music.Track;
import dev.qilletni.api.music.orchestration.OrderableTypeState;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.impl.lang.types.orderable.OrderableTracksTypeInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    private final MusicPopulator musicPopulator;
    private final OrderableTracksTypeInitializer orderableTracksTypeInitializer;

    public DefaultTrackOrchestrator(PlayActor playActor, MusicCache musicCache, MusicPopulator musicPopulator) {
        this.playActor = playActor;
        this.musicPopulator = musicPopulator;
        this.orderableTracksTypeInitializer = new OrderableTracksTypeInitializer(musicPopulator, musicCache);
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
        musicPopulator.populateCollection(collectionType); // The initial population in all these `playXYZ` methods are done before logging. Population also occurs in the OrderableTracksTypeInitializer
        
        LOGGER.debug("Play collection: {}", collectionType.getPlaylist().getTitle());

        conditionallyPlayTracks(collectionType, loop, track -> playActor.playTrack(track).join(), () -> true);
    }

    @Override
    public void playCollection(CollectionType collectionType, CollectionLimit collectionLimit) {
        musicPopulator.populateCollection(collectionType);
        
        LOGGER.debug("Play collection: {} with limit: {}", collectionType.getPlaylist().getTitle(), collectionLimit);

        playLimited(collectionType, collectionLimit);
    }

    @Override
    public void playAlbum(AlbumType albumType, boolean loop) {
        musicPopulator.populateAlbum(albumType);
        
        LOGGER.debug("Play album: {}", albumType.getAlbum().getName());
        
        conditionallyPlayTracks(albumType, loop, track -> playActor.playTrack(track).join(), () -> true);
    }

    @Override
    public void playAlbum(AlbumType albumType, CollectionLimit collectionLimit) {
        musicPopulator.populateAlbum(albumType);
        
        LOGGER.debug("Play album: {} with limit: {}", albumType.getAlbum().getName(), collectionLimit);

        playLimited(albumType, collectionLimit);
    }

    @Override
    public Track getTrackFromOrderableType(OrderableTypeState orderableTypeState) {
        
        // if sequential, just get the next track from the cached track/index
        if (orderableTypeState.getOrderableType().getOrder() == CollectionOrder.SEQUENTIAL) {
            int trackIndex = orderableTypeState.getAndIncrementSequentialIndex();
            return orderableTracksTypeInitializer.getTracks(orderableTypeState.getOrderableType()).get(trackIndex);
        }
        
        var atomicTrack = new AtomicReference<Track>();

        conditionallyPlayTracks(orderableTypeState.getOrderableType(), true, atomicTrack::set, () -> atomicTrack.get() == null);

        return atomicTrack.get();
    }

    @Override
    public Track getTrackFromWeight(WeightsType weightsType) {
        var atomicTrack = new AtomicReference<Track>();

        conditionallyPlayWeightedTracks(Collections.emptyList(), weightsType, CollectionOrder.SHUFFLE, true, atomicTrack::set, () -> atomicTrack.get() == null);

        return atomicTrack.get();
    }
    
    private void playLimited(OrderableTracksType orderableTracksType, CollectionLimit collectionLimit) {
        if (collectionLimit.limitUnit().isTimeBased()) {
            playTimeLimited(orderableTracksType, collectionLimit.calculateLimitMilliseconds());
        } else {
            playCountLimited(orderableTracksType, collectionLimit.limitCount());
        }
    }

    private void playTimeLimited(OrderableTracksType orderableTracksType, long limitMilliseconds) {
        var totalTimePassed = new AtomicInteger();

        conditionallyPlayTracks(orderableTracksType, true, track -> {
                    totalTimePassed.addAndGet(track.getDuration());
                    playActor.playTrack(track).join();
                },
                () -> totalTimePassed.get() < limitMilliseconds);
    }

    private void playCountLimited(OrderableTracksType orderableTracksType, int count) {
        var totalTracksPlayed = new AtomicInteger();

        conditionallyPlayTracks(orderableTracksType, true, track -> {
                    totalTracksPlayed.incrementAndGet();
                    playActor.playTrack(track).join();
                },
                () -> totalTracksPlayed.get() < count);
    }
    
    private void conditionallyPlayTracks(OrderableTracksType orderableTracksType, boolean loop, Consumer<Track> playCallback, Supplier<Boolean> shouldPlayTrack) {
        var tracks = orderableTracksTypeInitializer.getTracks(orderableTracksType);

        conditionallyPlayWeightedTracks(tracks, orderableTracksType.getWeights(), orderableTracksType.getOrder(), loop, playCallback, shouldPlayTrack);
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
