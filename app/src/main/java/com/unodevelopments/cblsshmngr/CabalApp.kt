package com.unodevelopments.cblsshmngr

import android.app.Application
import androidx.room.Room
import com.unodevelopments.cblsshmngr.data.AppDatabase
import com.unodevelopments.cblsshmngr.data.ConnectionRepository
import com.unodevelopments.cblsshmngr.data.PasswordCipher
import com.unodevelopments.cblsshmngr.data.SqlRepository
import com.unodevelopments.cblsshmngr.ssh.CryptoSetup
import com.unodevelopments.cblsshmngr.ui.theme.ThemeStore

class CabalApp : Application() {
    lateinit var repository: ConnectionRepository
        private set

    lateinit var sqlRepository: SqlRepository
        private set

    lateinit var themeStore: ThemeStore
        private set

    override fun onCreate() {
        super.onCreate()
        themeStore = ThemeStore(this)
        CryptoSetup.install()
        val cipher = PasswordCipher()
        val database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "cabal-ssh.db",
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
        repository = ConnectionRepository(database.connectionDao(), cipher, database.savedCommandDao())
        sqlRepository = SqlRepository(
            database.sqlConnectionDao(),
            database.connectionDao(),
            cipher,
            database.queryHistoryDao(),
        )
    }
}
