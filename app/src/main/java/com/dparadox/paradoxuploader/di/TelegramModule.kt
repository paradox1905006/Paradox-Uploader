package com.dparadox.paradoxuploader.di

import com.dparadox.paradoxuploader.data.telegram.FileDownloadRepositoryImpl
import com.dparadox.paradoxuploader.data.telegram.LocalDirectLinkRepository
import com.dparadox.paradoxuploader.data.telegram.TdFileReader
import com.dparadox.paradoxuploader.data.telegram.ArchiveRepositoryImpl
import com.dparadox.paradoxuploader.data.telegram.UploadControllerImpl
import com.dparadox.paradoxuploader.data.telegram.UploadRepositoryImpl
import com.dparadox.paradoxuploader.domain.telegram.UploadController
import com.dparadox.paradoxuploader.domain.telegram.UploadRepository
import com.dparadox.paradoxuploader.data.telegram.TelegramAuthRepositoryImpl
import com.dparadox.paradoxuploader.domain.telegram.ArchiveRepository
import com.dparadox.paradoxuploader.data.telegram.TelegramPrefsImpl
import com.dparadox.paradoxuploader.data.telegram.TelegramResolverRepositoryImpl
import com.dparadox.paradoxuploader.domain.telegram.DirectLinkRepository
import com.dparadox.paradoxuploader.domain.telegram.FileDownloadRepository
import com.dparadox.paradoxuploader.domain.telegram.RemoteFileReader
import com.dparadox.paradoxuploader.domain.telegram.TelegramAuthRepository
import com.dparadox.paradoxuploader.domain.telegram.TelegramPrefs
import com.dparadox.paradoxuploader.domain.telegram.TelegramResolverRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TelegramModule {
    @Binds abstract fun bindAuth(impl: TelegramAuthRepositoryImpl): TelegramAuthRepository
    @Binds abstract fun bindResolver(impl: TelegramResolverRepositoryImpl): TelegramResolverRepository
    @Binds abstract fun bindLinks(impl: LocalDirectLinkRepository): DirectLinkRepository
    @Binds abstract fun bindDownloads(impl: FileDownloadRepositoryImpl): FileDownloadRepository
    @Binds abstract fun bindPrefs(impl: TelegramPrefsImpl): TelegramPrefs
    @Binds abstract fun bindReader(impl: TdFileReader): RemoteFileReader
    @Binds abstract fun bindArchive(impl: ArchiveRepositoryImpl): ArchiveRepository
    @Binds abstract fun bindUploadRepo(impl: UploadRepositoryImpl): UploadRepository
    @Binds abstract fun bindUploadController(impl: UploadControllerImpl): UploadController
}
