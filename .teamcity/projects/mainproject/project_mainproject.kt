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

        // Create templates via registry by key (map-driven, concise)
        val templateStreams = mapOf(
            "default"        to "//streams/default",
            "client-ios"     to "//streams/client-ios",
            "client-android" to "//streams/client-android",
            "server"         to "//streams/server",
            "tools"          to "//streams/tools",
            "assets"         to "//streams/assets"
        )
        val templates = templateStreams.mapValues { (key, stream) ->
            TemplateRegistry.get(key).create("${idp}_tpl_${key.replace('-', '_')}", vcsRoot, stream)
        }
        templates.values.forEach { root.template(it) }

        // Branch-first hierarchy: define branches and all leaf paths globally
        val branches = listOf("branch1", "branch2")
        val leafPaths = listOf(
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

        // Build rules from pattern -> template-key pairs
        val rules = listOf(
            "client/ios/**"      to "client-ios",
            "client/android/**"  to "client-android",
            "server/**"          to "server",
            "tools/**"           to "tools",
            "assets/**"          to "assets"
        ).map { (pattern, key) -> R(pattern, templates.getValue(key)) }

        buildForestFromPaths(
            root       = root,
            idp        = idp,
            branches   = branches,
            leafPaths  = leafPaths,
            rules      = rules,
            defaultTpl = templates.getValue("default"),
            vcsRoot    = vcsRoot
        )
    }
}

// Register on load
@Suppress("unused")
private val __register = ProjectRegistry.register(Configurator)
