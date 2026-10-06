package com.unodevelopments.cblsshmngr.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SshConnection::class,
        SqlConnection::class,
        SavedCommand::class,
        QueryHistory::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao

    abstract fun sqlConnectionDao(): SqlConnectionDao

    abstract fun savedCommandDao(): SavedCommandDao

    abstract fun queryHistoryDao(): QueryHistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sql_connections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sshConnectionId` INTEGER NOT NULL,
                        `sqlHost` TEXT NOT NULL,
                        `sqlPort` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `passwordCipher` TEXT NOT NULL,
                        `databaseName` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_commands` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sshConnectionId` INTEGER NOT NULL,
                        `label` TEXT NOT NULL,
                        `command` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `query_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sqlConnectionId` INTEGER NOT NULL,
                        `sql` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
