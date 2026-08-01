package com.example.data.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import java.io.File

object CoverArtCache {
    // Memory cache for decoded bitmaps (max 150 bitmaps to avoid OOM and prevent thrashing)
    private val memoryCache = LruCache<String, Bitmap>(150)
    // Synchronized cache of keys known to have no embedded picture (to prevent repeatedly calling heavy MediaMetadataRetriever)
    private val noCoverEmptyKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun getInMemory(trackId: String, resolution: String): Bitmap? {
        val cacheKey = "${trackId}_${resolution}"
        return memoryCache.get(cacheKey)
    }

    fun isKnownNoCover(trackId: String, resolution: String): Boolean {
        val cacheKey = "${trackId}_${resolution}"
        return noCoverEmptyKeys.contains(cacheKey)
    }

    fun get(trackId: String, path: String, resolution: String): Bitmap? {
        if (path.isEmpty()) return null
        
        val cacheKey = "${trackId}_${resolution}"
        if (noCoverEmptyKeys.contains(cacheKey)) {
            return null
        }
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val bitmap = loadAndProcessBitmap(path, resolution)
        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        } else {
            noCoverEmptyKeys.add(cacheKey)
        }
        return bitmap
    }

    private fun loadAndProcessBitmap(path: String, resolution: String): Bitmap? {
        try {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                val bytes = retriever.embeddedPicture
                if (bytes == null || bytes.isEmpty()) return null

                if (resolution == "original") {
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    // Optimized resolution
                    val targetSize = when (resolution) {
                        "low" -> 160
                        "medium" -> 350
                        else -> 600 // "optimized" value
                    }
                    
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    
                    var sampleSize = 1
                    while ((options.outWidth / sampleSize) > targetSize || (options.outHeight / sampleSize) > targetSize) {
                        sampleSize *= 2
                    }
                    
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                }
            } catch (ex: Exception) {
                Log.e("CoverArtCache", "Error reading embedded picture from track $path: ${ex.message}")
            } finally {
                try {
                    retriever.release()
                } catch (re: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    fun clear() {
        memoryCache.evictAll()
        noCoverEmptyKeys.clear()
    }

    fun getCacheSize(): Int {
        return memoryCache.size()
    }

    fun getNoCoverCount(): Int {
        return noCoverEmptyKeys.size
    }
}
