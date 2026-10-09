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
import dev.qilletni.api.music.orchestration.CollectionState;
import dev.qilletni.api.music.orchestration.OrderableTypeState;
import dev.qilletni.api.music.orchestration.TrackOrchestrator;
import dev.qilletni.api.music.play.PlayActor;
import dev.qilletni.impl.lang.exceptions.UnplayableTypeException;
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
    public void playWeights(WeightsType weightsType, CollectionLimit collectionLimit) {
        if (!WeightUtils.areWeightsConstrained(weightsType)) {
            throw new UnplayableTypeException("Unable to play unconstrained weights. All weight entries must be a percent and add up to 100% in order to play directly.");
        }
        
        LOGGER.debug("Playing weights: {} with limit: {}", weightsType.stringValue(), collectionLimit);

        playWeightsLimited(weightsType, collectionLimit);
    }

    @Override
    public void playCollection(CollectionType collectionType, boolean loop) {
        musicPopulator.populateCollection(collectionType); // The initial population in all these `playXYZ` methods are done before logging. Population also occurs in the OrderableTracksTypeInitializer
        
        LOGGER.debug("Play collection: {}", collectionType.getPlaylist().getTitle());

        conditionallyPlayOrderableTracks(collectionType, loop, track -> playActor.playTrack(track).join(), () -> true);
    }

    @Override
    public void playCollection(CollectionType collectionType, CollectionLimit collectionLimit) {
        musicPopulator.populateCollection(collectionType);
        
        LOGGER.debug("Play collection: {} with limit: {}", collectionType.getPlaylist().getTitle(), collectionLimit);

        playOrderableLimited(collectionType, collectionLimit);
    }

    @Override
    public void playAlbum(AlbumType albumType, boolean loop) {
        musicPopulator.populateAlbum(albumType);
        
        LOGGER.debug("Play album: {}", albumType.getAlbum().getName());
        
        conditionallyPlayOrderableTracks(albumType, loop, track -> playActor.playTrack(track).join(), () -> true);
    }

    @Override
    public void playAlbum(AlbumType albumType, CollectionLimit collectionLimit) {
        musicPopulator.populateAlbum(albumType);
        
        LOGGER.debug("Play album: {} with limit: {}", albumType.getAlbum().getName(), collectionLimit);

        playOrderableLimited(albumType, collectionLimit);
    }

    @Override
    @SuppressWarnings("removal")
    public Track getTrackFromCollection(CollectionState collectionState) {
        return getTrackFromOrderableType(collectionState);
    }

    @Override
    public Track getTrackFromOrderableType(OrderableTypeState orderableTypeState) {
        
        // if sequential, just get the next track from the cached track/index
        if (orderableTypeState.getOrderableType().getOrder() == CollectionOrder.SEQUENTIAL) {
            int trackIndex = orderableTypeState.getAndIncrementSequentialIndex();
            return orderableTracksTypeInitializer.getTracks(orderableTypeState.getOrderableType()).get(trackIndex);
        }
        
        var atomicTrack = new AtomicReference<Track>();

        conditionallyPlayOrderableTracks(orderableTypeState.getOrderableType(), true, atomicTrack::set, () -> atomicTrack.get() == null);

        return atomicTrack.get();
    }

    @Override
    public Track getTrackFromWeight(WeightsType weightsType) {
        var atomicTrack = new AtomicReference<Track>();

        var weightedTrackPlayer = new WeightedTrackPlayer(weightsType, atomicTrack::set, () -> atomicTrack.get() == null);
        weightedTrackPlayer.conditionallyPlayWeights();

        return atomicTrack.get();
    }
    
    private void playOrderableLimited(OrderableTracksType orderableTracksType, CollectionLimit collectionLimit) {
        var tracks = orderableTracksTypeInitializer.getTracks(orderableTracksType);
        var weights = orderableTracksType.getWeights();
        var order = orderableTracksType.getOrder();

        var weightedTrackPlayer = WeightedTrackPlayer.byCollectionLimit(weights, collectionLimit, track -> playActor.playTrack(track).join());
        weightedTrackPlayer.conditionallyPlayWeightedTracks(tracks, order, true);
    }
    
    private void playWeightsLimited(WeightsType weights, CollectionLimit collectionLimit) {
        var weightedTrackPlayer = WeightedTrackPlayer.byCollectionLimit(weights, collectionLimit, track -> playActor.playTrack(track).join());
        weightedTrackPlayer.conditionallyPlayWeights();
    }
    
    private void conditionallyPlayOrderableTracks(OrderableTracksType orderableTracksType, boolean loop, Consumer<Track> playCallback, Supplier<Boolean> shouldPlayTrack) {
        var tracks = orderableTracksTypeInitializer.getTracks(orderableTracksType);
        var weightedTrackPlayer = new WeightedTrackPlayer(orderableTracksType.getWeights(), playCallback, shouldPlayTrack);
        weightedTrackPlayer.conditionallyPlayWeightedTracks(tracks, orderableTracksType.getOrder(), loop);
    }
    
    private static class WeightedTrackPlayer {
        private final Supplier<Boolean> shouldPlayTrack;
        private final WeightsType weights;
        private final Consumer<Track> playCallback;
        
        private WeightDispersion weightDispersion;

        // If the weighted track chosen is this, grab one from the queue instead
        private Track dontPlayNextTrack;
        private WeightEntry dontPlayNextWeight;
        
        WeightedTrackPlayer(WeightsType weights, Consumer<Track> playCallback, Supplier<Boolean> shouldPlayTrack) {
            this.weights = weights;
            this.playCallback = playCallback;
            this.shouldPlayTrack = shouldPlayTrack;
            this.dontPlayNextTrack = null;
            this.dontPlayNextWeight = null;
        }
        
        private void initializeWeights() {
            WeightUtils.validateWeights(weights);
            this.weightDispersion = WeightDispersion.initializeWeightDispersion(weights);
        }

        /**
         * Creates a {@link WeightedTrackPlayer} either via {@link #byCount( WeightsType, Consumer, int)} or
         * {@link #byTime( WeightsType, Consumer, long)} depending on the {@link CollectionLimit} provided;
         * 
         * @param weights The weights to use when playing tracks
         * @param collectionLimit The limit to use when playing tracks
         * @param playCallback The callback to invoke when a track is played
         * @return The created {@link WeightedTrackPlayer}
         */
        public static WeightedTrackPlayer byCollectionLimit(WeightsType weights, CollectionLimit collectionLimit, Consumer<Track> playCallback) {
            if (collectionLimit.limitUnit().isTimeBased()) {
                return WeightedTrackPlayer.byTime(weights, playCallback, collectionLimit.calculateLimitMilliseconds());
            } else {
                return WeightedTrackPlayer.byCount(weights, playCallback, collectionLimit.limitCount());
            }
        }

        /**
         * Returns a {@link WeightedTrackPlayer} that will supply {@code count} tracks based on the provided weights to
         * the {@code playCallback}.
         * 
         * @param weights The weights to use when playing tracks
         * @param playCallback The callback to invoke when a track is played
         * @param count The number of tracks to play
         * @return The created {@link WeightedTrackPlayer}
         */
        public static WeightedTrackPlayer byCount(WeightsType weights, Consumer<Track> playCallback, int count) {
            var totalTracksPlayed = new AtomicInteger();

            return new WeightedTrackPlayer(weights, track -> {
                totalTracksPlayed.incrementAndGet();
                playCallback.accept(track);
            }, () -> totalTracksPlayed.get() < count);
        }

        /**
         * Returns a {@link WeightedTrackPlayer} that will supply tracks based on the provided weights to
         * the {@code playCallback} until the provided limit is reached. This is greedy, so it will aim to play at
         * minimum the provided limit, and may play a song through the limit.
         * 
         * @param weights The weights to use when playing tracks
         * @param playCallback The callback to invoke when a track is played
         * @param limitMilliseconds The milliseconds to play tracks for
         * @return The created {@link WeightedTrackPlayer}
         */
        public static WeightedTrackPlayer byTime(WeightsType weights, Consumer<Track> playCallback, long limitMilliseconds) {
            var totalTimePassed = new AtomicInteger();

            return new WeightedTrackPlayer(weights, track -> {
                totalTimePassed.addAndGet(track.getDuration());
                playCallback.accept(track);
            }, () -> totalTimePassed.get() < limitMilliseconds);
        }

        /**
         * Plays tracks from the given list while {@link #shouldPlayTrack} allows it.
         * <p>
         * If the order is {@link CollectionOrder#SEQUENTIAL}, tracks are played in order and weights are ignored.
         * Otherwise, percentage-weighted tracks are removed from the list, multiplier weights are applied, and the
         * result is shuffled into a queue. Each iteration first attempts a weighted selection, falling back to the
         * next track in the queue if no weight was selected.
         *
         * @param tracks The tracks to play
         * @param collectionOrder The order to play the tracks in
         * @param loop If the queue should be reshuffled and replayed once empty
         */
        private void conditionallyPlayWeightedTracks(List<Track> tracks, CollectionOrder collectionOrder, boolean loop) {
            if (collectionOrder == CollectionOrder.SEQUENTIAL) {
                for (Track track : tracks) {
                    if (!shouldPlayTrack.get()) {
                        break;
                    }

                    playCallback.accept(track);
                }

                return;
            }

            initializeWeights();

            tracks = prunePercentageWeightedTracks(tracks, weights);

            boolean initiallyEmpty = tracks.isEmpty();
            Queue<Track> trackQueue = applyWeights(tracks, weights);

            boolean isRetry = false;

            processWhile: while ((initiallyEmpty || !trackQueue.isEmpty()) && (isRetry || shouldPlayTrack.get())) {
                isRetry = false;

                switch (selectWeightedTrack()) {
                    case RETRY:
                        isRetry = true;
                        continue processWhile;
                    case TRACK_SELECTED:
                        continue processWhile;
                    case NO_SELECTION: break;
                }

                if (!initiallyEmpty) {
                    var playingTrack = trackQueue.poll();
                    playCallback.accept(playingTrack);
                    dontPlayNextTrack = null;
                    dontPlayNextWeight = null;

                    if (trackQueue.isEmpty() && loop) {
                        trackQueue = applyWeights(tracks, weights);
                        trackQueue = ensureNoBeginningDuplicate(trackQueue, playingTrack);

                        LOGGER.debug("Reapplying weights to: {}", trackQueue);
                    }
                }
            }
        }

        /**
         * Plays tracks selected solely from the weights while {@link #shouldPlayTrack} allows it. There is no
         * backing track list, so the weights are expected to be constrained (all percentages adding up to 100%).
         */
        private void conditionallyPlayWeights() {
            initializeWeights();
            
            boolean isRetry = false;

            processWhile: while (isRetry || shouldPlayTrack.get()) {
                isRetry = false;

                switch (selectWeightedTrack()) {
                    case RETRY:
                        isRetry = true;
                        break;
                    case NO_SELECTION: break;
                    case TRACK_SELECTED:
                        continue processWhile; // No additional selection logic; try again
                }
            }
        }

        /**
         * Selects a weighted track from the weight dispersion.
         *
         * @return How the parent algorithm should continue selecting songs (if at all).
         */
        private SelectionResult selectWeightedTrack() {
            // Select a song from the weight. This may run this same method again if coming from another weight
            var weightedTrackContextOptional = weightDispersion.selectWeight();

            if (weightedTrackContextOptional.isPresent()) {
                var weightEntry = weightedTrackContextOptional.get();
                var weightedTrack = weightEntry.getTrack();

                // If the track or weight can't be repeated (and it was played last), don't play it
                if (weightedTrack.equals(dontPlayNextTrack) || weightEntry.equals(dontPlayNextWeight)) {
                    return SelectionResult.RETRY;
                }

                playCallback.accept(weightedTrack);

                // If only the current track can't be repeated, don't play the next track
                if (!weightEntry.getCanRepeatTrack()) {
                    dontPlayNextTrack = weightedTrack;
                }

                // If the weight can't be repeated, don't play the next track OR weight
                if (!weightEntry.getCanRepeatWeight()) {
                    dontPlayNextTrack = weightedTrack;
                    dontPlayNextWeight = weightEntry;
                }

                return SelectionResult.TRACK_SELECTED;
            }

            return SelectionResult.NO_SELECTION;
        }

        
        /**
         * Adjusts the given queue of tracks to ensure that the first track in the queue is not
         * the same as the previously played track. If the first track is a duplicate of the 
         * previously played track, it will be repositioned randomly within the queue.
         *
         * @param tracks The queue of tracks to be adjusted.
         * @param previouslyPlayed The track that was played most recently.
         * @return A queue of tracks with the duplicate track at the beginning repositioned, 
         *         or the original queue if no adjustment was necessary.
         */ 
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
         * Removes tracks from the provided list based on percentage-weighted entries in the given weights.
         * If the weights are null, the original list of tracks is returned unchanged.
         *
         * @param tracks The list of {@link Track} objects to be filtered.
         * @param weights The {@link WeightsType} object containing weight entries that determine tracks
         *                to be excluded based on percentage-based conditions.
         * @return A filtered list of {@link Track} objects, excluding tracks that match the percentage-weighted
         */
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

        /**
         * Applies weight-based adjustments to a given list of tracks to create a weighted queue. 
         * If weights are provided, the method modifies the probabilities of tracks appearing
         * in the resulting queue based on the multiplier weights. If no weights are provided or 
         * the list of tracks is empty, the queue remains unmodified.
         *
         * @param tracks The list of {@link Track} objects to be processed.
         * @param weights The {@link WeightsType} object containing weight entries that determine
         *                how tracks should be weighted in the queue. If null, tracks are not 
         *                adjusted based on weights.
         * @return A shuffled {@link Queue} of {@link Track}s
         */
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

        /**
         * Enum representing the result of a selection operation from {@link #selectWeightedTrack()}.
         * This is passed to a caller method to determine the outcome of the selection process.
         */
        enum SelectionResult {
            /**
             * A track has been selected.
             */
            TRACK_SELECTED,
            
            /**
             * No track has been selected, so continue with selecting.
             */
            NO_SELECTION,
            
            /**
             * A selection was made but was invalid, so the receiver of this type should retry selection.
             */
            RETRY
        }
    }
}
