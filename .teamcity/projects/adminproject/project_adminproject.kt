package projects.adminproject

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import lib.ProjectConfigurator
import lib.ProjectRegistry
import projects.adminproject.config.AdminConfig

object Configurator : ProjectConfigurator {
    override val name: String = AdminConfig.name
    override fun configure(root: Project) {
        val idp = AdminConfig.idp
        val admin = Project { id("${idp}_Prj_${AdminConfig.adminProjectKey}"); name = AdminConfig.adminProjectName }
        root.subProject(admin)
        val util = BuildType {
            id("${idp}_BT_${AdminConfig.utilitiesBuildTypeKey}")
            name = AdminConfig.utilitiesBuildTypeName
            steps {
                script {
                    name = AdminConfig.utilitiesStepName
                    scriptContent = AdminConfig.utilitiesScript
                }
            }
        }
        admin.buildType(util)
    }
}

// Register on load
@Suppress("unused")
private val __register = ProjectRegistry.register(Configurator)
