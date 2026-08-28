package com.voxengine.engine

import android.content.Context
import com.voxengine.data.SettingsRepository
import com.voxengine.engine.edge.EdgeTTSEngine
import com.voxengine.engine.local.LocalTTSEngine
import com.voxengine.engine.mimo.MiMoEngine

object EngineBootstrap {
    @Synchronized
    fun ensureRegistered(settingsRepository: SettingsRepository, appContext: Context) {
        if (!EngineRegistry.isRegistered("mimo")) {
            EngineRegistry.register(MiMoEngine(settingsRepository))
        }
        if (!EngineRegistry.isRegistered("edge")) {
            EngineRegistry.register(EdgeTTSEngine(settingsRepository))
        }
        if (!EngineRegistry.isRegistered("local")) {
            EngineRegistry.register(LocalTTSEngine(appContext.applicationContext))
        }
    }
}
