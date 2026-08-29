package com.example.riesgossocialesenchapinero.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MensajeEntity::class, HechoEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mensajeDao(): MensajeDao
    abstract fun hechoDao(): HechoDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barrio_seguro.db",
                ).build().also { instancia = it }
            }
    }
}
