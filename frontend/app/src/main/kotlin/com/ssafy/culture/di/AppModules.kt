package com.ssafy.culture.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.data.auth.AuthHeaderInterceptor
import com.ssafy.culture.data.auth.AuthTokenRefreshAuthenticator
import com.ssafy.culture.data.local.AppDatabase
import com.ssafy.culture.data.local.CultureDao
import com.ssafy.culture.data.remote.CultureApi
import com.ssafy.culture.data.repository.CultureRepository
import com.ssafy.culture.data.repository.DefaultCultureRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authHeaderInterceptor: AuthHeaderInterceptor,
        authTokenRefreshAuthenticator: AuthTokenRefreshAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(NetworkConnectTimeout)
            .readTimeout(NetworkReadTimeout)
            .writeTimeout(NetworkWriteTimeout)
            .callTimeout(NetworkCallTimeout)
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(authTokenRefreshAuthenticator)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideCultureApi(
        retrofit: Retrofit,
    ): CultureApi = retrofit.create(CultureApi::class.java)

    private val NetworkConnectTimeout: Duration = Duration.ofSeconds(15)
    private val NetworkReadTimeout: Duration = Duration.ofSeconds(75)
    private val NetworkWriteTimeout: Duration = Duration.ofSeconds(30)
    private val NetworkCallTimeout: Duration = Duration.ofSeconds(90)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.NAME,
        )
            .build()

    @Provides
    fun provideCultureDao(
        appDatabase: AppDatabase,
    ): CultureDao = appDatabase.cultureDao()
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCultureRepository(
        defaultCultureRepository: DefaultCultureRepository,
    ): CultureRepository
}
