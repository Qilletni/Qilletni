
/**
 * When songs are played, they are added to the queue when the currently playing song ends. The first 2 songs will be
 * added immediately as a buffer, but sequential ones will wait for the song to end before being added to the queue to
 * not spam the queue.
 */
fun enableBlockingQueue() {
    enableBlockingQueue(false)
}

/**
 * When songs are played, they are added to the queue when the currently playing song ends. The first 2 songs will be
 * added immediately as a buffer, but sequential ones will wait for the song to end before being added to the queue to
 * not spam the queue.
 *
 * @param[@type boolean] fastPolling If `true`, the queue will be polled every 5 seconds. If `false`, it will be polled at roughly 75% of the time left on the current song.
 */
native fun enableBlockingQueue(fastPolling)

/**
 * When songs are played, they are added to the queue immediately.
 */
native fun disableBlockingQueue()
