package com.itu.videoeditor

import android.app.Application
import androidx.room.Room
import com.itu.videoeditor.data.StudioDatabase
import com.itu.videoeditor.data.StudioRepository

class ItuApplication : Application() {
    val repository by lazy {
        StudioRepository(this, Room.databaseBuilder(this, StudioDatabase::class.java, "itu-studio.db").build())
    }
}
