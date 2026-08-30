package com.example.riesgossocialesenchapinero.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MensajeEntity::class, HechoEntity::class, ConversacionEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mensajeDao(): MensajeDao
    abstract fun hechoDao(): HechoDao
    abstract fun conversacionDao(): ConversacionDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        fun obtener(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barrio_seguro.db",
                )
                    // No hay migraciones escritas todavía (app en desarrollo,
                    // sin usuarios reales): ante un cambio de esquema borra y
                    // recrea en vez de crashear.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instancia = it }
            }
    }
}
