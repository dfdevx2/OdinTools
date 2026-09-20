package de.langerhans.odintools.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppOverrideEntity::class],
    version = 3, // Atualizado para a nova arquitetura do Odin Hub (TDP/Clocks)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appOverrideDao(): AppOverrideDao
}
