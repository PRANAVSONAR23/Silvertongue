package com.silvertongue.paraphraser.paraphrase

import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private fun failureForStatus(code: Int): ParaphraseFailure = when {
    code == 429 -> ParaphraseFailure.RATE_LIMITED
    code == 404 -> ParaphraseFailure.MODEL_UNAVAILABLE
    code >= 500 -> ParaphraseFailure.SERVER_ERROR
    else -> ParaphraseFailure.BAD_RESPONSE
}

suspend fun OkHttpClient.awaitResponseBody(request: Request, providerName: String): String {
    val response = suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(
                    ParaphraseException(
                        "$providerName request failed: ${e.message}",
                        ParaphraseFailure.TRANSPORT,
                        e
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(Result.success(response))
            }
        })
    }

    response.use {
        val body = it.body?.string().orEmpty()
        if (!it.isSuccessful) {
            throw ParaphraseException(
                "$providerName returned ${it.code}: ${body.take(300)}",
                failureForStatus(it.code)
            )
        }
        if (body.isBlank()) {
            throw ParaphraseException(
                "$providerName returned an empty response",
                ParaphraseFailure.BAD_RESPONSE
            )
        }
        return body
    }
}
