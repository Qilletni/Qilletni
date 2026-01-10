/**
 * A Queue implementation, can hold any type.
 */
entity Queue {

    any[] _queue = []
    
    /**
     * Creates a new queue from a list of elements.
     *
     * @param[@type list] list A list of elements
     * @returns[@type core.Queue] A new queue with the given elements
     */
    static fun fromList(list) {
        Queue queue = new Queue()
        queue._queue = list
        return queue
    }
    
    /**
     * Enqueues an element onto the queue.
     *
     * @param element The element to enqueue onto the queue
     */
    fun enqueue(element) {
        _queue.add(element)
    }
    
    /**
     * Dequeues an element from the queue.
     *
     * @returns The element dequeued from the queue
     * @errors If the queue is empty
     */
    fun dequeue() {
        return _queue.remove(0)
    }
    
    /**
     * Peeks at the front element of the queue.
     *
     * @returns The front element of the queue
     * @errors If the queue is empty
     */
    fun peek() {
        return _queue[0]
    }
    
    /**
     * Checks if the queue is empty.
     *
     * @returns[@type boolean] true if the queue is empty, false if otherwise
     */
    fun isEmpty() {
        return _queue.isEmpty()
    }
    
    /**
     * Gets the size of the queue.
     *
     * @returns[@type int] The size of the queue
     */
    fun size() {
        return _queue.size()
    }
    
    /**
     * Converts the queue to a list.
     *
     * @returns[@type list] The queue as a list
     */
    fun toList() {
        return _queue
    }
}