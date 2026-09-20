package de.langerhans.odintools.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppOverrideEntity::class],
    version = 5, // v5: adiciona limitMode + valores numéricos reais de TDP/Clock/Fan por jogo
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appOverrideDao(): AppOverrideDao

    companion object {
        /**
         * Antes desta auditoria, qualquer alteração de schema dependia de
         * `fallbackToDestructiveMigration()` (ver DatabaseModule), que apaga TODAS as regras
         * por jogo do utilizador a cada bump de versão. Esta migração preserva os dados já
         * existentes ao subir de v4 para v5: os perfis (nomes) continuam os mesmos, só ganham
         * colunas novas para os valores numéricos e o modo de limitação por jogo.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE appoverride ADD COLUMN limitMode TEXT NOT NULL DEFAULT 'TDP'")
                db.execSQL("ALTER TABLE appoverride ADD COLUMN tdpWatts REAL")
                db.execSQL("ALTER TABLE appoverride ADD COLUMN perfClockKHz INTEGER")
                db.execSQL("ALTER TABLE appoverride ADD COLUMN primeClockKHz INTEGER")
                db.execSQL("ALTER TABLE appoverride ADD COLUMN gpuClockHz INTEGER")
                db.execSQL("ALTER TABLE appoverride ADD COLUMN fanSettingsValue INTEGER")
            }
        }
    }
}