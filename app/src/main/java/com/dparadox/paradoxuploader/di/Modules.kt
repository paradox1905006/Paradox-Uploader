package com.dparadox.paradoxuploader.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.dparadox.paradoxuploader.BuildConfig
import com.dparadox.paradoxuploader.data.local.AndroidSourceFiles
import com.dparadox.paradoxuploader.data.local.AppDatabase
import com.dparadox.paradoxuploader.data.local.DataStoreSettingsRepository
import com.dparadox.paradoxuploader.data.local.FolderDao
import com.dparadox.paradoxuploader.data.local.MIGRATION_1_2
import com.dparadox.paradoxuploader.data.local.MIGRATION_2_3
import com.dparadox.paradoxuploader.data.local.SecureKeyStore
import com.dparadox.paradoxuploader.data.local.UploadDao
import com.dparadox.paradoxuploader.data.remote.OkHttpArchiveService
import com.dparadox.paradoxuploader.data.remote.OkHttpUpdateService
import com.dparadox.paradoxuploader.data.repository.CleanupRepositoryImpl
import com.dparadox.paradoxuploader.data.repository.FolderRepositoryImpl
import com.dparadox.paradoxuploader.data.repository.HistoryRepositoryImpl
import com.dparadox.paradoxuploader.data.repository.UploadRepositoryImpl
import com.dparadox.paradoxuploader.domain.repository.ArchiveService
import com.dparadox.paradoxuploader.domain.repository.CleanupRepository
import com.dparadox.paradoxuploader.domain.repository.FolderRepository
import com.dparadox.paradoxuploader.domain.repository.HistoryRepository
import com.dparadox.paradoxuploader.domain.repository.KeyStore
import com.dparadox.paradoxuploader.domain.repository.SettingsRepository
import com.dparadox.paradoxuploader.domain.repository.SourceFiles
import com.dparadox.paradoxuploader.domain.repository.UpdateService
import com.dparadox.paradoxuploader.domain.repository.UploadRepository
import com.dparadox.paradoxuploader.domain.repository.WorkScheduler
import com.dparadox.paradoxuploader.domain.util.Clock
import com.dparadox.paradoxuploader.domain.util.DeviceClock
import com.dparadox.paradoxuploader.work.WorkManagerScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
        if (BuildConfig.DEBUG) {
            // Headers only (never bodies), and the Authorization header is always redacted.
            val logging = HttpLoggingInterceptor { message -> Log.d("ParadoxHttp", message) }
            logging.level = HttpLoggingInterceptor.Level.HEADERS
            logging.redactHeader("Authorization")
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "paradox.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideDao(db: AppDatabase): UploadDao = db.uploadDao()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds abstract fun bindArchive(impl: OkHttpArchiveService): ArchiveService
    @Binds abstract fun bindUpdates(impl: OkHttpUpdateService): UpdateService
    @Binds abstract fun bindRepository(impl: UploadRepositoryImpl): UploadRepository
    @Binds abstract fun bindCleanup(impl: CleanupRepositoryImpl): CleanupRepository
    @Binds abstract fun bindFolders(impl: FolderRepositoryImpl): FolderRepository
    @Binds abstract fun bindHistory(impl: HistoryRepositoryImpl): HistoryRepository
    @Binds abstract fun bindKeys(impl: SecureKeyStore): KeyStore
    @Binds abstract fun bindSettings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun bindSources(impl: AndroidSourceFiles): SourceFiles
    @Binds abstract fun bindScheduler(impl: WorkManagerScheduler): WorkScheduler
    @Binds abstract fun bindClock(impl: DeviceClock): Clock
}
