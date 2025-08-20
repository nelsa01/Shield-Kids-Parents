package com.shieldtechhub.shieldkids.common.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

object ImageLoader {
    fun loadInto(context: Context, imageView: ImageView, uriString: String, placeholderResId: Int) {
        if (uriString.isEmpty()) {
            imageView.setImageResource(placeholderResId)
            return
        }

        if (uriString.startsWith("http")) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val url = URL(uriString)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.instanceFollowRedirects = true
                        connection.connectTimeout = 8000
                        connection.readTimeout = 15000
                        connection.useCaches = true
                        connection.connect()
                        connection.inputStream.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    }
                    imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.w("ImageLoader", "Failed to load image: ${e.localizedMessage}")
                    imageView.setImageResource(placeholderResId)
                }
            }
        } else {
            try {
                val uri = Uri.parse(uriString)
                imageView.setImageURI(uri)
            } catch (e: Exception) {
                Log.w("ImageLoader", "Invalid URI: ${e.localizedMessage}")
                imageView.setImageResource(placeholderResId)
            }
        }
    }
}


