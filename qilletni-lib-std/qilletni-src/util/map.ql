/**
 * A [@java java.util.HashMap] wrapper that may store keys and values of any type.
 */
entity Map {

    /**
     * The internal [@java java.util.HashMap] object, storing the map's state.
     * @type @java java.util.HashMap
     */
    java _map = Map._emptyJavaMap()

    /**
     * Creates an empty [@java java.util.HashMap] instance.
     *
     * @returns[@type @java java.util.HashMap] An instance of a [@java java.util.HashMap]
     */
    native static fun _emptyJavaMap()
    
    /**
     * Creates a new map from a list of key-value pairs.
     *
     * @param[@type list] list A list of key-value pairs
     * @returns[@type core.Map] A new map with the given key-value pairs
     */
    static fun fromList(list) {
        Map map = new Map()
        int i = 0
        for (i < list.size()) {
            map.put(list[i++], list[i++])
        }
        
        return map
    }
    
    /**
     * Puts a [@param key] and [@param value] into the map, overriding any previous value associated with the key.
     *
     * @param[@type string]                  key   The key to add into the map
     * @param[@type @java java.util.HashMap] value The value to be associated with the key
     * @errors If the [@param key] is not found
     */
    native fun put(key, value)
    
    /**
     * Gets a value from the map by its [@param key].
     *
     * @param key  The key to look for
     * @returns The value associated with the given key
     * @errors If the [@param key] is not found
     */
    native fun get(key)
    
    /**
     * Checks if the map contains the key [@param key].
     *
     * @param key  The key to look for
     * @returns[@type boolean] true if the map contains the key, false if otherwise  
     */
    native fun containsKey(key)
    
    /**
     * Checks if the map contains any entry with the value [@param value].
     *
     * @param value  The value to look for
     * @returns[@type boolean] true if the map contains the value, false if otherwise  
     */
    native fun containsValue(value)
    
    /**
     * Gets a list of all keys in the map.
     *
     * @returns[@type list] A list of all keys in the map  
     */
    native fun keys()
    
    /**
     * Gets a list of all values in the map.
     *
     * @returns[@type list] A list of all values in the map  
     */
    native fun values()
}
