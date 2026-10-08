package com.cheatbuddy.wear.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.cheatbuddy.wear.data.ImageStore
import com.cheatbuddy.wear.data.SheetRepository
import com.cheatbuddy.wear.data.WatchPreferences
import com.cheatbuddy.wear.data.db.WatchDatabase
import com.cheatbuddy.wear.sync.PlayServicesWearableGateway
import com.cheatbuddy.wear.sync.SyncScheduler
import com.cheatbuddy.wear.sync.WatchSyncEngine
import com.cheatbuddy.wear.sync.WearableGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WatchDatabase =
        Room.databaseBuilder(context, WatchDatabase::class.java, WatchDatabase.NAME).build()

    @Provides
    @Singleton
    fun imageStore(@ApplicationContext context: Context) = ImageStore(File(context.filesDir, "images"))

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context) = WatchPreferences(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("watch_prefs") },
    )

    @Provides
    @Singleton
    fun repository(db: WatchDatabase, imageStore: ImageStore, preferences: WatchPreferences) =
        SheetRepository(db.sheetDao(), db.imageDao(), imageStore, preferences)

    @Provides
    @Singleton
    fun gateway(@ApplicationContext context: Context): WearableGateway = PlayServicesWearableGateway(context)

    @Provides
    fun syncEngine(
        gateway: WearableGateway,
        db: WatchDatabase,
        imageStore: ImageStore,
        preferences: WatchPreferences,
    ) = WatchSyncEngine(gateway, db.sheetDao(), db.imageDao(), imageStore, preferences)

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    fun scheduler(workManager: WorkManager) = SyncScheduler(workManager)
}
