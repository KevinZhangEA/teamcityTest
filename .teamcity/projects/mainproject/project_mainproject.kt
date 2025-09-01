package projects.mainproject

import jetbrains.buildServer.configs.kotlin.*
import lib.*
import lib.TemplateRule as R
import lib.buildForestFromPaths

// Top-level entry to configure the main project tree
fun configureMainProject(root: Project) {
    val idp = "v2"

    // Use the repository VCS root
    val vcsRoot = DslContext.settingsRoot

    // Create and register projects.templates on the root (P4-only)
    val tplDefault = defaultTemplate("${idp}_tpl_default", vcsRoot, "//streams/default")
    val tplCliIOS  = clientIosTemplate("${idp}_tpl_client_ios", vcsRoot, "//streams/client-ios")
    val tplCliAnd  = clientAndroidTemplate("${idp}_tpl_client_android", vcsRoot, "//streams/client-android")
    val tplServer  = serverTemplate("${idp}_tpl_server", vcsRoot, "//streams/server")
    val tplTools   = toolsTemplate("${idp}_tpl_tools", vcsRoot, "//streams/tools")
    val tplAssets  = assetsTemplate("${idp}_tpl_assets", vcsRoot, "//streams/assets")
    listOf(tplDefault, tplCliIOS, tplCliAnd, tplServer, tplTools, tplAssets).forEach { root.template(it) }

    // Per-Project configs (branches/paths/rules)
    data class ProjCfg(
        val key: String,
        val name: String,
        val branches: List<String>,
        val leafPaths: List<String>,
        val rules: List<R>
    )

    val projectsCfg = listOf(
        ProjCfg(
            key = "Client",
            name = "Client",
            branches = listOf("develop", "release"),
            leafPaths = listOf(
                "client/ios/debug", "client/ios/retail",
                "client/android/debug", "client/android/retail"
            ),
            rules = listOf(
                R("client/ios/**",     tplCliIOS),
                R("client/android/**", tplCliAnd)
            )
        ),
        ProjCfg(
            key = "Server",
            name = "Server",
            branches = listOf("develop", "release"),
            leafPaths = listOf(
                "server/game", "server/relay", "server/blaze", "server/stargate"
            ),
            rules = listOf(
                R("server/**", tplServer)
            )
        ),
        ProjCfg(
            key = "Content",
            name = "Content (Tools & Assets)",
            branches = listOf("main"),
            leafPaths = listOf(
                "tools/tools", "tools/tools1", "tools/tools2", "tools/tools3",
                "assets/scripts", "assets/players", "assets/stadium", "assets/cinematics",
                "assets/audio", "assets/designconfigs",
                "assets/ui/textures", "assets/ui/layouts", "assets/ui/localization", "assets/ui/fonts", "assets/ui/videos"
            ),
            rules = listOf(
                R("tools/**",  tplTools),
                R("assets/**", tplAssets)
            )
        )
    )

    // Create sibling projects under root and build forests under each
    projectsCfg.forEach { cfg ->
        val prj = Project { id("${idp}_Prj_${cfg.key}"); name = cfg.name }
        root.subProject(prj)
        buildForestFromPaths(
            root       = prj,
            idp        = "${idp}_${cfg.key}",
            branches   = cfg.branches,
            leafPaths  = cfg.leafPaths,
            rules      = cfg.rules,
            defaultTpl = tplDefault,
            vcsRoot    = vcsRoot
        )
    }
}
