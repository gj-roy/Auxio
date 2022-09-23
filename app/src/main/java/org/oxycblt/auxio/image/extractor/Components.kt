/*
 * Copyright (c) 2021 Auxio Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.image.extractor

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import coil.size.Size
import okio.buffer
import okio.source
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Music
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.music.Sort
import kotlin.math.min

/** A basic keyer for music data. */
class MusicKeyer : Keyer<Music> {
    override fun key(data: Music, options: Options) =
        if (data is Song) {
            // Group up song covers with album covers for better caching
            data.album.uid.toString()
        } else {
            data.uid.toString()
        }
}

/**
 * Fetcher that returns the album cover for a given [Album] or [Song], depending on the factory
 * used.
 * @author OxygenCobalt
 */
class AlbumCoverFetcher
private constructor(private val context: Context, private val album: Album) : BaseFetcher() {
    override suspend fun fetch(): FetchResult? =
        fetchArt(context, album)?.let { stream ->
            SourceResult(
                source = ImageSource(stream.source().buffer(), context),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        }

    class SongFactory : Fetcher.Factory<Song> {
        override fun create(data: Song, options: Options, imageLoader: ImageLoader) =
            AlbumCoverFetcher(options.context, data.album)
    }

    class AlbumFactory : Fetcher.Factory<Album> {
        override fun create(data: Album, options: Options, imageLoader: ImageLoader) =
            AlbumCoverFetcher(options.context, data)
    }
}

/**
 * Fetcher that fetches the image for an [Artist]
 * @author OxygenCobalt
 */
class ArtistImageFetcher
private constructor(
    private val context: Context,
    private val size: Size,
    private val artist: Artist
) : BaseFetcher() {
    override suspend fun fetch(): FetchResult? {
        val albums = Sort(Sort.Mode.ByName, true).albums(artist.albums)
        val results = albums.mapAtMost(4) { album -> fetchArt(context, album) }
        return createMosaic(context, results, size)
    }

    class Factory : Fetcher.Factory<Artist> {
        override fun create(data: Artist, options: Options, imageLoader: ImageLoader) =
            ArtistImageFetcher(options.context, options.size, data)
    }
}

/**
 * Fetcher that fetches the image for a [Genre]
 * @author OxygenCobalt
 */
class GenreImageFetcher
private constructor(
    private val context: Context,
    private val size: Size,
    private val genre: Genre
) : BaseFetcher() {
    override suspend fun fetch(): FetchResult? {
        val results = genre.albums.mapAtMost(4) { fetchArt(context, it) }

        return createMosaic(context, results, size)
    }

    class Factory : Fetcher.Factory<Genre> {
        override fun create(data: Genre, options: Options, imageLoader: ImageLoader) =
            GenreImageFetcher(options.context, options.size, data)
    }
}

/**
 * Map at most [n] items from a collection. [transform] is called for each item that is eligible. If
 * null is returned, then that item will be skipped.
 */
private inline fun <T : Any, R : Any> Collection<T>.mapAtMost(
    n: Int,
    transform: (T) -> R?
): List<R> {
    val until = min(size, n)
    val out = mutableListOf<R>()

    for (item in this) {
        if (out.size < until) {
            transform(item)?.let(out::add)
        } else {
            break
        }
    }

    return out
}
