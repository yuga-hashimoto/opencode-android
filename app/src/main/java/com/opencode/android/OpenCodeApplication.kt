package com.opencode.android

import android.app.Application
import com.opencode.android.data.repository.AppRepository
import com.opencode.android.data.connection.SecureSettingsRepository

class OpenCodeApplication : Application() {
    lateinit var settings: SecureSettingsRepository
        private set

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsRepository(this)
        repository = AppRepository(settings)
    }
}
