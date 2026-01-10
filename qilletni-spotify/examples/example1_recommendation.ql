import "spotify:recommendations.ql"
import "spotify:play_redirect.ql"
import "spotify:playlist_tools.ql"
import "std:util/queue.ql"

weights powerRotation =
    | 85% ["Monarch" by "Glasswaves",
            "Wants I Need" by "156/Silence",
            "Anti-Saviour" by "Voluntary Victim",
            "Millstone" by "ROSARY",
            "Hell (I let the Devil In)" by "Breakwaters",
            "Distance" by "Sleep Waker",
            "Claustrophobic" by "Before I Turn"]
    | 15% ["Heavy Rain" by "Konami Kode",
            "Puke" by "No Life",
            "Decay" by "Elwood Stray",
            "Blood Petals (feat. Julian Latouche)" by "Revoid",
            "All or Nothing" by "Foundations",
            "Melatonin" by "Sleep Waker"]

weights metalWeights =
   |~ 25% powerRotation

collection metalSongs = "My Playlist #59" collection by "rubbaboy" weights[metalWeights]

song[] songList = []
redirectPlayToList(songList)

// Keep a rolling history of the last 5 songs played
Queue history = new Queue()

fun onPlay(sng) {
    songList.add(sng)
    print("Playing " + sng.getTitle() + " by " + sng.getArtist().getName() + "   total: " + songList.size())
    history.enqueue(sng)

    if (history.size() > 5) {
        history.dequeue()
    }
}

redirectPlayToFunction(onPlay)

for (songList.size() < 50) {
    play metalSongs limit[5]

    // Recommend 3 tracks similar to the last 5 played, preferring lower popularity
    Recommender recommender = new Recommender()
        ..seedTracks = history.toList()
        ..targetPopularity = 10

    for (track : recommender.recommend(3)) {
        play track
    }
}

// Add all played songs to a new playlist

Date date = Date.now()
collection myPlaylist = createPlaylist("ahh Playlist w/ recs %d/%d".format([date.getMonth(), date.getDay()]))

print("created playlist " + myPlaylist)

addToPlaylist(myPlaylist, songList)
print("Added " + songList)
