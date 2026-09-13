// Retrofit 工厂 + Auth 拦截器（§7.4 JWT Bearer）
package com.elder.android.network

import com.elder.android.data.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    private val tokenStore: TokenStore,
    private val accountBaseUrl: String,
    private val reminderBaseUrl: String,
    private val agentBaseUrl: String,
    private val cosBaseUrl: String,
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        // anonymous-device / bind 端点不需 Bearer（§C / §3.2.7，v2.1.2）
        val path = req.url.encodedPath
        val isPublic = path.endsWith("/v1/auth/anonymous-device") ||
            path.contains("/v1/bind/code") ||
            path.contains("/v1/bind/pending") ||
            path.contains("/v1/bind/confirm")
        val token = if (isPublic) null else runBlocking { tokenStore.current()?.token }
        val newReq = if (token == null) req else req.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        chain.proceed(newReq)
    }

    private fun client(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(log)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val accountApi: AccountApi = retrofit(accountBaseUrl).create(AccountApi::class.java)
    val reminderApi: ReminderApi = retrofit(reminderBaseUrl).create(ReminderApi::class.java)
    val agentApi: AgentApi = retrofit(agentBaseUrl).create(AgentApi::class.java)
    val cosApi: CosApi = retrofit(cosBaseUrl).create(CosApi::class.java)
}
