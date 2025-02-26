package eu.kanade.tachiyomi.animeextension.all.kamyroll

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.Locale

class AccessTokenInterceptor(
    private val crUrl: String,
    private val json: Json,
    private val preferences: SharedPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessTokenN = getAccessToken()

        val request = newRequestWithAccessToken(chain.request(), accessTokenN)
        val response = chain.proceed(request)
        when (response.code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                synchronized(this) {
                    response.close()
                    // Access token is refreshed in another thread. Check if it has changed.
                    val newAccessToken = getAccessToken()
                    if (accessTokenN != newAccessToken) {
                        return chain.proceed(newRequestWithAccessToken(request, newAccessToken))
                    }
                    val refreshedToken = refreshAccessToken(TOKEN_PREF_KEY)
                    // Retry the request
                    return chain.proceed(
                        newRequestWithAccessToken(chain.request(), refreshedToken),
                    )
                }
            }
            else -> return response
        }
    }

    private fun newRequestWithAccessToken(request: Request, tokenData: AccessToken): Request {
        return request.newBuilder()
            .header("authorization", "${tokenData.token_type} ${tokenData.access_token}")
            .build()
    }

    fun getAccessToken(): AccessToken {
        return preferences.getString(TOKEN_PREF_KEY, null)?.toAccessToken()
            ?: refreshAccessToken(TOKEN_PREF_KEY)
    }

    fun getLocalToken(force: Boolean = false): AccessToken? {
        if (!preferences.getBoolean(PREF_FETCH_LOCAL_SUBS, false) && !force) return null
        synchronized(this) {
            val now = System.currentTimeMillis() + 1800000 // add 30 minutes for safety
            val localToken = preferences.getString(LOCAL_TOKEN_PREF_KEY, null)?.toAccessToken()
            return if (force || localToken == null || localToken.policyExpire!! < now) {
                refreshAccessToken(LOCAL_TOKEN_PREF_KEY, false)
            } else {
                localToken
            }
        }
    }

    fun removeLocalToken() {
        preferences.edit().putString(LOCAL_TOKEN_PREF_KEY, null).apply()
    }

    private fun refreshAccessToken(PREF_KEY: String, useProxy: Boolean = true): AccessToken {
        val client = OkHttpClient().newBuilder().build()
        Authenticator.setDefault(
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication("crunblocker", "crunblocker".toCharArray())
                }
            },
        )
        val usedClient = if (useProxy) {
            client.newBuilder()
                .proxy(
                    Proxy(
                        Proxy.Type.SOCKS,
                        InetSocketAddress("cr-unblocker.us.to", 1080),
                    ),
                )
                .build()
        } else {
            client
        }
        val response = usedClient.newCall(getRequest(client)).execute()
        val parsedJson = json.decodeFromString<AccessToken>(response.body.string())

        val policy = usedClient.newCall(newRequestWithAccessToken(GET("$crUrl/index/v2"), parsedJson)).execute()
        val policyJson = json.decodeFromString<Policy>(policy.body.string())
        val allTokens = AccessToken(
            parsedJson.access_token,
            parsedJson.token_type,
            policyJson.cms.policy,
            policyJson.cms.signature,
            policyJson.cms.key_pair_id,
            policyJson.cms.bucket,
            DateFormatter.parse(policyJson.cms.expires)?.time,
        )
        preferences.edit().putString(PREF_KEY, allTokens.toJsonString()).apply()
        return allTokens
    }

    private fun getRequest(client: OkHttpClient): Request {
        val refreshTokenResp = client.newCall(
            GET("https://raw.githubusercontent.com/Samfun75/File-host/main/aniyomi/refreshToken.txt"),
        ).execute()
        val refreshToken = refreshTokenResp.body.string().replace("[\n\r]".toRegex(), "")
        val headers = Headers.headersOf(
            "Content-Type", "application/x-www-form-urlencoded",
            "Authorization", "Basic a3ZvcGlzdXZ6Yy0teG96Y21kMXk6R21JSTExenVPVnRnTjdlSWZrSlpibzVuLTRHTlZ0cU8="
        )
        val postBody = "grant_type=refresh_token&refresh_token=$refreshToken&scope=offline_access".toRequestBody(
            "application/x-www-form-urlencoded".toMediaType(),
        )
        return POST("$crUrl/auth/v1/token", headers, postBody)
    }

    private fun AccessToken.toJsonString(): String {
        return json.encodeToString(this)
    }

    private fun String.toAccessToken(): AccessToken {
        return json.decodeFromString(this)
    }

    companion object {
        private const val TOKEN_PREF_KEY = "access_token_data"
        private const val LOCAL_TOKEN_PREF_KEY = "local_access_token_data"
        private const val PREF_FETCH_LOCAL_SUBS = "preferred_local_subs"

        private val DateFormatter by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        }
    }
}
