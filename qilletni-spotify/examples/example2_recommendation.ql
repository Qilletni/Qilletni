import "spotify:recommendations.ql"
import "spotify:play_redirect.ql"
import "spotify:playlist_tools.ql"
import "std:util/stack.ql"

weights powerRotation =
    | 85% ["Wants I Need" by "156/Silence", "Monarch" by "Glasswaves",
            "Anti-Saviour" by "Voluntary Victim",
            "Millstone" by "ROSARY",
            "Hell (I let the Devil In)" by "Breakwaters",
            "Distance" by "Sleep Waker",
            "Claustrophobic" by "Before I Turn"]
    | 15% ["Heavy Rain" by "Konami Kode",
            "Decay" by "Elwood Stray",
            "All or Nothing" by "Foundations"]

weights metalWeights =
   |~ 30% powerRotation

collection metalSongs = "My Playlist #59" collection by "rubbaboy" weights[metalWeights]

song[] songList = []
redirectPlayToList(songList)

// All recommended songs
Stack recommendations = new Stack()

fun generateUniqueRecommendations() {
    Recommender recommender = new Recommender()
            ..seedTracks = ["Truth Serum" by "Gutter King", 
                            "Paradise" by "Dark Island",
                            "Anti-Saviour" by "Voluntary Victim",
                            "Spiral" by "Feyn Entity",
                            "Dark Tunnel" by "Beholder"]
            ..targetEnergy = 1.0
            ..targetPopularity = 10

    song[] recs = recommender.recommend(100)

    for (rec : recs) {
        if (!metalSongs.containsArtist(rec.getArtist())) {
            recommendations.push(rec)
        }
    }
    
    print("Generated %d unique recommendations".format([recommendations.size()]))
}

generateUniqueRecommendations()

// Play 2 shuffled songs from metal playlist, then 3 recommendations
for (recommendations.size() > 0) {
    play recommendations.pop()
}

recommendations = new Stack()

generateUniqueRecommendations()

for (recommendations.size() > 0) {
    play recommendations.pop()
}

//for (recommendations.size() >= 3) {
//    play metalSongs limit[2]
//
//    for (i..3) {
//        play recommendations.pop()
//    }
//}

// Add all played songs to a new playlist
Date date = Date.now()
collection myPlaylist = createPlaylist("Recs only %d/%d".format([date.getMonth(), date.getDay()]))

addToPlaylist(myPlaylist, songList)
print("Created a playlist with %s songs".format([songList.size()]))
