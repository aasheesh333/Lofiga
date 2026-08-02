package com.dhanuk.lofiga.coil

import android.media.MediaMetadataRetriever
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import java.io.File

class AlbumArtFetcherFactory : Fetcher.Factory<AlbumArtKey> {
    override fun create(
        data: AlbumArtKey,
        options: Options,
        imageLoader: ImageLoader
    ): Fetcher {
        return AlbumArtFetcher(data, options)
    }
}

private class AlbumArtFetcher(
    private val key: AlbumArtKey,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            val path = key.dataPath
            if (!path.isNullOrBlank() && File(path).exists()) {
                retriever.setDataSource(path)
            } else if (key.audioUri != null) {
                retriever.setDataSource(options.context, key.audioUri)
            } else {
                runCatching { retriever.release() }
                return null
            }
            val picture = retriever.embeddedPicture
            if (picture == null || picture.isEmpty()) {
                runCatching { retriever.release() }
                return null
            }
            val buffer = Buffer().apply { write(picture) }
            SourceFetchResult(
                source = ImageSource(buffer, options.context),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            runCatching { retriever.release() }
            null
        }
    }
}
