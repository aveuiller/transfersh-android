package io.github.aveuiller.transfersh

import android.content.Context
import android.net.Uri
import android.os.Build
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File


class TransferService(
    private val client: OkHttpClient,
    private val url: String,
    private val applicationContext: Context
) {
    fun upload(data: Uri, onResult: Callback) {
        val request = Request.Builder()
            .post(generateBody(data))
            .url(url)
            .header("Accept", "text/plain")
            .header("Content-Type", "multipart/form-data")
            .build()

        val call = client.newCall(request)
        call.enqueue(onResult)
    }

    private fun generateBody(data: Uri): RequestBody {
        val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            File(FileUtil.getPath(data, applicationContext))
        } else {
            File(data.path)

        }
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("data", data.path, file.asRequestBody("*/*".toMediaTypeOrNull()))
            .addFormDataPart("name", data.path!!)
            .build()
    }
}
