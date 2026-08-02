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
import okio.FileSystem
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
            val loaded = when {
                !path.isNullOrBlank() && File(path).exists() -> {
                    runCatching { retriever.setDataSource(path) }.isSuccess
                }
                key.audioUri != null -> {
                    runCatching { retriever.setDataSource(options.context, key.audioUri) }.isSuccess
                }
                else -> false
            }
            if (!loaded) {
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
                source = ImageSource(
                    source = buffer,
                    fileSystem = FileSystem.SYSTEM
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.MEMORY
            )
        } catch (e: Exception) {
            runCatching { retriever.release() }
            null
        }
    }
}
