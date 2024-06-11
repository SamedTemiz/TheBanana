package com.timrashard.banana

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream

class AssetManager(private val context: Context) {
    private val service: Service

    init {
        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        service = retrofit.create(Service::class.java)
    }

    suspend fun downloadAssets(owner: String, repo: String, path: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                downloadAndSaveFiles(owner, repo, path)
            } catch (e: Exception) {
                Log.e("AssetManager", "Error downloading assets", e)
                false
            }
        }
    }

    private fun downloadAndSaveFiles(owner: String, repo: String, path: String): Boolean {
        val response = service.getContents(owner, repo, path).execute()

        if (!response.isSuccessful) {
            Log.e("AssetManager", "Failed to get repository contents: ${response.code()} ${response.message()}")
            return false
        }

        val files = response.body() ?: return false
        for (file in files) {
            if (file.type == "dir") {
                if (!downloadAndSaveFiles(owner, repo, file.path)) {
                    return false
                }
            } else if (file.type == "file" && !file.download_url.isNullOrEmpty()) {
                val downloadUrl = file.download_url
                if (!downloadFile(downloadUrl, file.name)) {
                    return false
                }
            }
        }
        return true
    }

    private fun downloadFile(downloadUrl: String, fileName: String): Boolean {
        return try {
            val response = service.downloadFile(downloadUrl).execute()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    saveFile(fileName, body)
                } else {
                    Log.e("AssetManager", "File download failed: Response body is null")
                    false
                }
            } else {
                Log.e("AssetManager", "File download failed: ${response.code()} ${response.message()}")
                false
            }
        } catch (e: Exception) {
            Log.e("AssetManager", "Error downloading file", e)
            false
        }
    }

    private fun saveFile(fileName: String, body: ResponseBody): Boolean {
        return try {
            val directory = File(context.getExternalFilesDir(null), "emojis")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, fileName)
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("AssetManager", "File saved successfully: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("AssetManager", "Error saving file", e)
            false
        }
    }
}