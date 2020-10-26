package org.oxycblt.auxio.playback

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.music.toDuration
import org.oxycblt.auxio.playback.state.PlaybackMode
import org.oxycblt.auxio.playback.state.PlaybackStateCallback
import org.oxycblt.auxio.playback.state.PlaybackStateManager

// A ViewModel that acts as an intermediary between the UI and PlaybackStateManager
// TODO: Implement Looping Modes
// TODO: Implement User Queue
// TODO: Implement Persistence through Bundles/Databases/Idk
class PlaybackViewModel(private val context: Context) : ViewModel(), PlaybackStateCallback {
    // Playback
    private val mSong = MutableLiveData<Song>()
    val song: LiveData<Song> get() = mSong

    private val mPosition = MutableLiveData(0L)
    val position: LiveData<Long> get() = mPosition

    // Queue
    private val mQueue = MutableLiveData(mutableListOf<Song>())
    val queue: LiveData<MutableList<Song>> get() = mQueue

    private val mIndex = MutableLiveData(0)
    val index: LiveData<Int> get() = mIndex

    // States
    private val mIsPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> get() = mIsPlaying

    private val mIsShuffling = MutableLiveData(false)
    val isShuffling: LiveData<Boolean> get() = mIsShuffling

    // Other
    private val mIsSeeking = MutableLiveData(false)
    val isSeeking: LiveData<Boolean> get() = mIsSeeking

    val formattedPosition = Transformations.map(mPosition) {
        it.toDuration()
    }

    val positionAsProgress = Transformations.map(mPosition) {
        if (mSong.value != null) it.toInt() else 0
    }

    val nextItemsInQueue = Transformations.map(mQueue) {
        it.slice((mIndex.value!! + 1) until it.size)
    }

    private var mCanAnimate = false
    val canAnimate: Boolean get() = mCanAnimate

    // Service setup
    private val playbackManager = PlaybackStateManager.getInstance()

    init {
        // Start the service from the ViewModel.
        // Yes, I know ViewModels aren't supposed to deal with this stuff but for some
        // reason it only works here.
        Intent(context, PlaybackService::class.java).also {
            context.startService(it)
        }

        playbackManager.addCallback(this)

        // If the PlaybackViewModel was cleared [signified by the PlaybackStateManager having a
        // song and the fact that were are in the init function], then try to restore the playback
        // state.
        if (playbackManager.song != null) {
            restorePlaybackState()
        }
    }

    // --- PLAYING FUNCTIONS ---

    // Play a song
    fun playSong(song: Song, mode: PlaybackMode) {
        playbackManager.playSong(song, mode)
    }

    // Play all songs
    fun shuffleAll() {
        playbackManager.shuffleAll()
    }

    // Play an album
    fun playAlbum(album: Album, shuffled: Boolean) {
        if (album.songs.isEmpty()) {
            Log.e(this::class.simpleName, "Album is empty, Not playing.")

            return
        }

        playbackManager.playParentModel(album, shuffled)
    }

    // Play an artist
    fun playArtist(artist: Artist, shuffled: Boolean) {
        if (artist.songs.isEmpty()) {
            Log.e(this::class.simpleName, "Artist is empty, Not playing.")

            return
        }

        playbackManager.playParentModel(artist, shuffled)
    }

    // Play a genre
    fun playGenre(genre: Genre, shuffled: Boolean) {
        if (genre.songs.isEmpty()) {
            Log.e(this::class.simpleName, "Genre is empty, Not playing.")

            return
        }

        playbackManager.playParentModel(genre, shuffled)
    }

    // --- POSITION FUNCTIONS ---

    // Update the position without pushing the change to playbackManager.
    // This is used during seek events to give the user an idea of where they're seeking to.
    fun updatePositionDisplay(progress: Int) {
        mPosition.value = progress.toLong()
    }

    // Update the position and push the change the playbackManager.
    fun updatePosition(progress: Int) {
        playbackManager.seekTo(progress.toLong())
    }

    // --- QUEUE FUNCTIONS ---

    // Skip to next song.
    fun skipNext() {
        playbackManager.next()
    }

    // Skip to last song.
    fun skipPrev() {
        playbackManager.prev()
    }

    // Remove a queue item, given a QueueAdapter index.
    fun removeQueueItem(adapterIndex: Int) {
        // Translate the adapter indices into the correct queue indices
        val delta = mQueue.value!!.size - nextItemsInQueue.value!!.size

        val index = adapterIndex + delta

        playbackManager.removeQueueItem(index)
    }

    // Move queue items, given QueueAdapter indices.
    fun moveQueueItems(adapterFrom: Int, adapterTo: Int) {
        // Translate the adapter indices into the correct queue indices
        val delta = mQueue.value!!.size - nextItemsInQueue.value!!.size

        val from = adapterFrom + delta
        val to = adapterTo + delta

        playbackManager.moveQueueItems(from, to)
    }

    // --- STATUS FUNCTIONS ---

    // Flip the playing status.
    fun invertPlayingStatus() {
        mCanAnimate = true

        playbackManager.setPlayingStatus(!playbackManager.isPlaying)
    }

    // Flip the shuffle status.
    fun invertShuffleStatus() {
        playbackManager.setShuffleStatus(!playbackManager.isShuffling)
    }

    // --- OTHER FUNCTIONS ---

    fun resetAnimStatus() {
        mCanAnimate = false
    }

    fun setSeekingStatus(value: Boolean) {
        mIsSeeking.value = value
    }

    // --- OVERRIDES ---

    override fun onCleared() {
        playbackManager.removeCallback(this)
    }

    override fun onSongUpdate(song: Song?) {
        song?.let {
            mSong.value = it
        }
    }

    override fun onPositionUpdate(position: Long) {
        if (!mIsSeeking.value!!) {
            mPosition.value = position
        }
    }

    override fun onQueueUpdate(queue: MutableList<Song>) {
        mQueue.value = queue
    }

    override fun onIndexUpdate(index: Int) {
        mIndex.value = index
    }

    override fun onPlayingUpdate(isPlaying: Boolean) {
        mIsPlaying.value = isPlaying
    }

    override fun onShuffleUpdate(isShuffling: Boolean) {
        mIsShuffling.value = isShuffling
    }

    private fun restorePlaybackState() {
        mSong.value = playbackManager.song
        mPosition.value = playbackManager.position
        mQueue.value = playbackManager.queue
        mIndex.value = playbackManager.index
        mIsPlaying.value = playbackManager.isPlaying
        mIsShuffling.value = playbackManager.isShuffling
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaybackViewModel::class.java)) {
                return PlaybackViewModel(context) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class.")
        }
    }
}