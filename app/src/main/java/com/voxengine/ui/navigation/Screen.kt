package com.voxengine.ui.navigation

sealed class Screen(val route: String, val title: String) {
    data object Settings : Screen("settings", "Settings")
    data object VoiceManage : Screen("voice_manage", "Voice")
    data object Reader : Screen("reader", "Reader")
    data object Test : Screen("test", "Test")
    data object About : Screen("about", "About")
    data object Log : Screen("log", "Logs")
}
