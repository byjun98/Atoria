package com.ssafy.culture.di

import com.ssafy.culture.data.remote.KakaoDirectionsApi
import com.ssafy.culture.data.remote.KakaoLocalApi
import com.ssafy.culture.data.repository.DefaultKakaoDirectionsRepository
import com.ssafy.culture.data.repository.DefaultKakaoLocalRepository
import com.ssafy.culture.data.repository.KakaoDirectionsRepository
import com.ssafy.culture.data.repository.KakaoLocalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KakaoLocalRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KakaoDirectionsRetrofit

@Module
@InstallIn(SingletonComponent::class)
object KakaoLocalNetworkModule {
    @Provides
    @Singleton
    @KakaoLocalRetrofit
    fun provideKakaoLocalRetrofit(
        loggingInterceptor: HttpLoggingInterceptor,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(KAKAO_LOCAL_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .build(),
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideKakaoLocalApi(
        @KakaoLocalRetrofit retrofit: Retrofit,
    ): KakaoLocalApi = retrofit.create(KakaoLocalApi::class.java)

    @Provides
    @Singleton
    @KakaoDirectionsRetrofit
    fun provideKakaoDirectionsRetrofit(
        loggingInterceptor: HttpLoggingInterceptor,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(KAKAO_DIRECTIONS_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .build(),
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideKakaoDirectionsApi(
        @KakaoDirectionsRetrofit retrofit: Retrofit,
    ): KakaoDirectionsApi = retrofit.create(KakaoDirectionsApi::class.java)

    private const val KAKAO_LOCAL_BASE_URL = "https://dapi.kakao.com/"
    private const val KAKAO_DIRECTIONS_BASE_URL = "https://apis-navi.kakaomobility.com/"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class KakaoLocalRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindKakaoLocalRepository(
        defaultKakaoLocalRepository: DefaultKakaoLocalRepository,
    ): KakaoLocalRepository

    @Binds
    @Singleton
    abstract fun bindKakaoDirectionsRepository(
        defaultKakaoDirectionsRepository: DefaultKakaoDirectionsRepository,
    ): KakaoDirectionsRepository
}
