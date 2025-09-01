package projects.mainproject

import jetbrains.buildServer.configs.kotlin.Project
import lib.ProjectConfigurator
import lib.ProjectRegistry
import lib.buildForestFromPaths
import lib.TemplateRegistry
import projects.mainproject.templates.TemplateList
import projects.mainproject.config.MainConfig
import lib.TemplateRule as R

object Configurator : ProjectConfigurator {
    override val name: String = MainConfig.name
    override fun configure(root: Project) {
        val idp = MainConfig.idp
        val vcsRoot = MainConfig.vcsRoot

        // Ensure mainproject template providers are loaded locally
        TemplateRegistry.loadProvidersByClassNames(TemplateList.enabled)

        // Create templates via registry by key (map-driven, concise)
        val templates = MainConfig.templateStreams.mapValues { (key, stream) ->
            TemplateRegistry.get(key).create("${idp}_tpl_${key.replace('-', '_')}", vcsRoot, stream)
        }
        templates.values.forEach { root.template(it) }

        // Branch-first hierarchy: define branches and all leaf paths globally (from config)
        val branches = MainConfig.branches
        val leafPaths = MainConfig.leafPaths

        // Build rules from pattern -> template-key pairs (from config)
        val rules = MainConfig.ruleMappings.map { (pattern, key) -> R(pattern, templates.getValue(key)) }

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
