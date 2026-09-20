package de.langerhans.odintools.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppOverrideEntity::class],
    version = 4, // Subindo para v4 para acomodar as colunas de Display, SGSR e LSFG
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appOverrideDao(): AppOverrideDao
}
