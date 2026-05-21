package com.ssafy.culture.di

import com.google.gson.Gson
import com.ssafy.culture.data.ebook.GeminiImageApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiImageRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiImageOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object GeminiImageNetworkModule {
    @Provides
    @Singleton
    @GeminiImageOkHttpClient
    fun provideGeminiImageOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(GeminiConnectTimeout)
            .readTimeout(GeminiReadTimeout)
            .writeTimeout(GeminiWriteTimeout)
            .callTimeout(GeminiCallTimeout)
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    @GeminiImageRetrofit
    fun provideGeminiImageRetrofit(
        @GeminiImageOkHttpClient okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(GeminiImageBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideGeminiImageApi(
        @GeminiImageRetrofit retrofit: Retrofit,
    ): GeminiImageApi = retrofit.create(GeminiImageApi::class.java)

    private const val GeminiImageBaseUrl =
        "https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com/"
    private val GeminiConnectTimeout: Duration = Duration.ofSeconds(20)
    private val GeminiReadTimeout: Duration = Duration.ofSeconds(120)
    private val GeminiWriteTimeout: Duration = Duration.ofSeconds(60)
    private val GeminiCallTimeout: Duration = Duration.ofSeconds(150)
}
