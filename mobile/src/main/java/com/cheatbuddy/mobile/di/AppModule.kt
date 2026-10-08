package com.cheatbuddy.mobile.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.cheatbuddy.mobile.data.ImageRepository
import com.cheatbuddy.mobile.data.SheetRepository
import com.cheatbuddy.mobile.data.db.PhoneDatabase
import com.cheatbuddy.mobile.sync.PhoneSyncEngine
import com.cheatbuddy.mobile.sync.PhoneSyncScheduler
import com.cheatbuddy.mobile.sync.PlayServicesPhoneGateway
import com.cheatbuddy.mobile.sync.SyncPreferences
import com.cheatbuddy.mobile.sync.SyncStatusRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope that outlives screens, e.g. to finish saving an edit after the editor closes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PhoneDatabase =
        Room.databaseBuilder(context, PhoneDatabase::class.java, PhoneDatabase.NAME).build()

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun scheduler(workManager: WorkManager) = PhoneSyncScheduler(workManager)

    @Provides
    @Singleton
    fun sheetRepository(db: PhoneDatabase, scheduler: PhoneSyncScheduler) = SheetRepository(db.sheetDao(), scheduler)

    @Provides
    @Singleton
    fun imageRepository(@ApplicationContext context: Context) = ImageRepository(context)

    @Provides
    @Singleton
    fun syncPreferences(@ApplicationContext context: Context) = SyncPreferences(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("sync") },
    )

    @Provides
    @Singleton
    fun gateway(@ApplicationContext context: Context) = PlayServicesPhoneGateway(context)

    @Provides
    fun syncEngine(
        sheets: SheetRepository,
        images: ImageRepository,
        gateway: PlayServicesPhoneGateway,
        preferences: SyncPreferences,
    ) = PhoneSyncEngine(
        loadSheets = sheets::allSheets,
        gateway = gateway,
        preferences = preferences,
        onPublished = { all -> images.deleteUnreferenced(all.flatMap { it.imageIds }.toSet()) },
    )

    @Provides
    @Singleton
    fun syncStatus(preferences: SyncPreferences, scheduler: PhoneSyncScheduler, gateway: PlayServicesPhoneGateway) =
        SyncStatusRepository(preferences, scheduler, gateway.observeWatchReachable())
}
