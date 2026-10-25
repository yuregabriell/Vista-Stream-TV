package com.ivor.openstream.di

import android.content.Context
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.cache.CacheBudget
import com.ivor.openstream.data.cache.ForceRefreshInterceptor
import com.ivor.openstream.data.cache.MetadataCacheHeadersInterceptor
import com.ivor.openstream.data.cache.StaleIfErrorInterceptor
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.GithubApi
import com.ivor.openstream.data.settings.AppDns
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.TmdbKeyStore
import com.ivor.openstream.data.settings.MetadataGatewayStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

import java.io.File
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        keyStore: TmdbKeyStore,
        gatewayStore: MetadataGatewayStore
    ): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val source = original.url
        val isTmdb = source.host == "api.themoviedb.org"
        val gateway = if (isTmdb) gatewayStore.current() else null

        val builder = source.newBuilder()
        if (isTmdb) {
            // Gateway accepts /api/tmdb/3/... (the Retrofit API base stays unchanged).
            if (gateway != null) {
                val endpoint = gateway.baseUrl.toHttpUrl()
                builder.scheme(endpoint.scheme)
                    .host(endpoint.host)
                    .port(endpoint.port)
                    .encodedPath("/api/tmdb" + source.encodedPath)
                    .removeAllQueryParameters("api_key")
            } else {
                // User-owned local key for private usage, never part of the APK.
                val personalKey = keyStore.current()
                if (personalKey.isNotBlank()) builder.setQueryParameter("api_key", personalKey)
            }
            if (source.queryParameter("language") == null)
                builder.addQueryParameter("language", "pt-BR")
            builder.setQueryParameter("include_adult", "false")
        }
        val requestBuilder = original.newBuilder().url(builder.build())
        if (gateway != null) requestBuilder.header("X-VS-Access-Token", gateway.accessToken)
        chain.proceed(requestBuilder.build())
    }

    /** TMDB answers on disk, sized by the Cache size setting (read once, at start). */
    @Provides
    @Singleton
    fun provideMetadataCache(@ApplicationContext context: Context, settings: AppSettingsStore): Cache {
        val directory = File(context.cacheDir, CacheBudget.METADATA_DIR)
        return Cache(directory, CacheBudget.bytesFor(directory, settings.current.cacheSize.metadataBytes))
    }

    /**
     * The TMDB client. The auth interceptor runs first so cached answers are keyed by the final
     * URL; [StaleIfErrorInterceptor] then falls back to the cache when TMDB can't answer, and
     * [MetadataCacheHeadersInterceptor] decides how long each answer stays fresh.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor, dns: AppDns, cache: Cache): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(dns)
            .cache(cache)
            .addInterceptor(authInterceptor)
            .addInterceptor(StaleIfErrorInterceptor())
            .addInterceptor(createLoggingInterceptor(detailed = BuildConfig.DEBUG))
            .addNetworkInterceptor(MetadataCacheHeadersInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("StreamingClient")
    fun provideStreamingOkHttpClient(dns: AppDns): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(createLoggingInterceptor(detailed = false))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("Tmdb")
    fun provideTmdbRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("Tmdb") retrofit: Retrofit): TmdbApi {
        return retrofit.create(TmdbApi::class.java)
    }

    /**
     * Same client and cache, but every answer is checked with TMDB first (pull to refresh). Still
     * falls back to the cache when offline, and what it fetches refreshes the cache for everyone.
     */
    @Provides
    @Singleton
    @Named(FRESH_TMDB)
    fun provideFreshTmdbApi(@Named("Tmdb") retrofit: Retrofit, okHttpClient: OkHttpClient): TmdbApi {
        val client = okHttpClient.newBuilder().addInterceptor(ForceRefreshInterceptor()).build()
        return retrofit.newBuilder().client(client).build().create(TmdbApi::class.java)
    }

    const val FRESH_TMDB = "TmdbFresh"

    @Provides
    @Singleton
    fun provideGithubApi(json: Json, dns: AppDns): GithubApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(
                OkHttpClient.Builder()
                    .dns(dns)
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Accept", "application/vnd.github+json")
                                .build()
                        )
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GithubApi::class.java)
    }
}
