import "spotify:recommendations.ql"
import "spotify:play_redirect.ql"
import "spotify:playlist_tools.ql"
import "std:util/queue.ql"

collection metalSongs = "9-24" collection by "rubbaboy"

Artist silence = new Artist("2qXHYRTtZytxMMfO9pW1V9", "156/Silence")
Artist avoid = new Artist("7rZJ1D1ERxrHNKTWwpZFVU", "AVOID")

if (metalSongs.containsArtist(silence)) {
    print("Found 156/Silence in metalSongs")
} else {
    print("156/Silence is NOT in metalSongs")
}

if (metalSongs.containsArtist(avoid)) {
    print("Found AVOID in metalSongs")
} else {
    print("AVOID is NOT in metalSongs")
}

fun predicateFn(sng) {
    boolean cond = sng.getTitle().contains("People")
    if (cond) {
        print("Found song with 'The' in title: " + sng.getTitle())
    } else {
        print("Song does NOT contain it: " + sng.getTitle())
    }
    
    return cond
}

if (metalSongs.anySongMatches(predicateFn)) {
    print("Found song with 'People' in title")
} else {
    print("No song with 'People' in title")
}
