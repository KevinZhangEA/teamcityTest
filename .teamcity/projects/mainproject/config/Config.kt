package projects.mainproject.config

import jetbrains.buildServer.configs.kotlin.DslContext

object MainConfig {
    // Project name
    val name: String = "mainproject"

    // Identifier prefix for this project
    val idp: String = "v2"

    // TeamCity settings VCS root for this project
    val vcsRoot = DslContext.settingsRoot

    // Template keys -> VCS stream paths
    val templateStreams: Map<String, String> = mapOf(
        "default"        to "//streams/default",
        "client-ios"     to "//streams/client-ios",
        "client-android" to "//streams/client-android",
        "server"         to "//streams/server",
        "tools"          to "//streams/tools",
        "assets"         to "//streams/assets"
    )

    // Branch-first hierarchy: branches and leaf paths
    val branches: List<String> = listOf("branch1", "branch2")

    val leafPaths: List<String> = listOf(
        // client
        "client/ios/debug", "client/ios/retail",
        "client/android/debug", "client/android/retail",
        // server
        "server/game", "server/relay", "server/blaze", "server/stargate",
        // tools
        "tools/tools", "tools/tools1", "tools/tools2", "tools/tools3",
        // assets
        "assets/scripts", "assets/players", "assets/stadium", "assets/cinematics",
        "assets/audio", "assets/designconfigs",
        // assets/ui
        "assets/ui/textures", "assets/ui/layouts", "assets/ui/localization", "assets/ui/fonts", "assets/ui/videos"
    )

    // Build rules as pattern -> template-key pairs
    val ruleMappings: List<Pair<String, String>> = listOf(
        "client/ios/**"      to "client-ios",
        "client/android/**"  to "client-android",
        "server/**"          to "server",
        "tools/**"           to "tools",
        "assets/**"          to "assets"
    )
}
