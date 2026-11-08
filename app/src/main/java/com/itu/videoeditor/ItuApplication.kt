package com.itu.videoeditor

import android.app.Application
import androidx.room.Room
import com.itu.videoeditor.data.StudioDatabase
import com.itu.videoeditor.data.StudioRepository

class ItuApplication : Application() {
    val thumbnails by lazy { com.itu.videoeditor.media.ThumbnailStore(this) }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        thumbnails.clearMemory()
    }
    val repository by lazy {
        StudioRepository(this, Room.databaseBuilder(this, StudioDatabase::class.java, "itu-studio.db").addMigrations(StudioDatabase.MIGRATION_1_2).build())
    }
}
