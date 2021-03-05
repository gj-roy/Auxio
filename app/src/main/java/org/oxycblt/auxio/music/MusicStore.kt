package org.oxycblt.auxio.music

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.auxio.logD
import org.oxycblt.auxio.logE
import java.lang.Exception

/**
 * The main storage for music items. Use [MusicStore.getInstance] to get the single instance of it.
 * @author OxygenCobalt
 */
class MusicStore private constructor() {
    private var mGenres = listOf<Genre>()
    val genres: List<Genre> get() = mGenres

    private var mArtists = listOf<Artist>()
    val artists: List<Artist> get() = mArtists

    private var mAlbums = listOf<Album>()
    val albums: List<Album> get() = mAlbums

    private var mSongs = listOf<Song>()
    val songs: List<Song> get() = mSongs

    /** All parent models (ex Albums, Artists) loaded by Auxio */
    val parents: List<Parent> by lazy {
        mutableListOf<Parent>().apply {
            addAll(mGenres)
            addAll(mArtists)
            addAll(mAlbums)
        }
    }

    /** Marker for whether the music loading process has successfully completed. */
    var loaded = false
        private set

    /**
     * Load/Sort the entire music library. Should always be ran on a coroutine.
     */
    suspend fun load(context: Context): Response {
        return withContext(Dispatchers.IO) {
            loadMusicInternal(context)
        }
    }

    /**
     * Do the actual music loading process internally.
     */
    private fun loadMusicInternal(context: Context): Response {
        logD("Starting initial music load...")

        try {
            val start = System.currentTimeMillis()

            val loader = MusicLoader(context)
            loader.load()

            if (loader.songs.isEmpty()) {
                return Response.NO_MUSIC
            }

            mSongs = loader.songs
            mAlbums = loader.albums
            mArtists = loader.artists
            mGenres = loader.genres

            logD("Music load completed successfully in ${System.currentTimeMillis() - start}ms.")
        } catch (e: Exception) {
            logE("Something went horribly wrong.")
            logE(e.stackTraceToString())

            return Response.FAILED
        }

        loaded = true

        return Response.SUCCESS
    }

    /**
     * Get the song for a file [uri].
     * @return The corresponding [Song] for this [uri], null if there isnt one.
     */
    fun getSongForUri(uri: Uri, resolver: ContentResolver): Song? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            val fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))

            return songs.find { it.fileName == fileName }
        }

        return null
    }

    /**
     * Responses that [MusicStore] sends back when a [load] call completes.
     */
    enum class Response {
        NO_MUSIC, NO_PERMS, FAILED, SUCCESS
    }

    companion object {
        @Volatile
        private var INSTANCE: MusicStore? = null

        /**
         * Get/Instantiate the single instance of [MusicStore].
         */
        fun getInstance(): MusicStore {
            val currentInstance = INSTANCE

            if (currentInstance != null) {
                return currentInstance
            }

            synchronized(this) {
                val newInstance = MusicStore()
                INSTANCE = newInstance
                return newInstance
            }
        }
    }
}
