package com.timrashard.banana

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface Service {
    @GET("repos/{owner}/{repo}/contents/{path}")
    fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): Call<List<FileModel>>

    @GET
    fun downloadFile(@Url fileUrl: String): Call<ResponseBody>
}