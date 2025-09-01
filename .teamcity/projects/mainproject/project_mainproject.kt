package projects.mainproject

import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Project
import lib.ProjectConfigurator
import lib.ProjectRegistry
import lib.buildForestFromPaths
import lib.TemplateRegistry
import projects.mainproject.templates.TemplateList
import lib.TemplateRule as R

object Configurator : ProjectConfigurator {
    override val name: String = "mainproject"
    override fun configure(root: Project) {
        val idp = "v2"
        val vcsRoot = DslContext.settingsRoot

        // Ensure mainproject template providers are loaded locally
        TemplateRegistry.loadProvidersByClassNames(TemplateList.enabled)

        // Create templates via registry by key
        val tplDefault = TemplateRegistry.get("default").create("${idp}_tpl_default", vcsRoot, "//streams/default")
        val tplCliIOS  = TemplateRegistry.get("client-ios").create("${idp}_tpl_client_ios", vcsRoot, "//streams/client-ios")
        val tplCliAnd  = TemplateRegistry.get("client-android").create("${idp}_tpl_client_android", vcsRoot, "//streams/client-android")
        val tplServer  = TemplateRegistry.get("server").create("${idp}_tpl_server", vcsRoot, "//streams/server")
        val tplTools   = TemplateRegistry.get("tools").create("${idp}_tpl_tools", vcsRoot, "//streams/tools")
        val tplAssets  = TemplateRegistry.get("assets").create("${idp}_tpl_assets", vcsRoot, "//streams/assets")
        listOf(tplDefault, tplCliIOS, tplCliAnd, tplServer, tplTools, tplAssets).forEach { root.template(it) }

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
}

// Register on load
@Suppress("unused")
private val __register = ProjectRegistry.register(Configurator)
