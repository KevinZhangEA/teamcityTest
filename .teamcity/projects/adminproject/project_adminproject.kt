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

        // Agent initialization builds for different operating systems
        val macosAgent = BuildType {
            id("${idp}_BT_Agent_Init_MacOS")
            name = "Agent Init: MacOS"

            params {
                param("agent", "", type = "select", "data_1" to "%teamcity.agents.macos%", "display" to "prompt")
            }

            // Only run on agents that don't have the "macos-initialized" tag
            requirements {
                doesNotExist("agent.macos-initialized")
                matches("teamcity.agent.jvm.os.name", "Mac OS X")
            }

            steps {
                script {
                    name = "Initialize MacOS Agent"
                    scriptContent = """
                        # MacOS agent initialization script
                        # TODO: Add specific initialization commands for MacOS
                        
                        # After successful initialization, add tag to agent
                        echo "##teamcity[setParameter name='agent.macos-initialized' value='true']"
                        echo "MacOS agent initialization completed successfully"
                    """.trimIndent()
                }
            }
        }
        admin.buildType(macosAgent)

        val linuxAgent = BuildType {
            id("${idp}_BT_Agent_Init_Linux")
            name = "Agent Init: Linux"

            params {
                param("agent", "", type = "select", "data_1" to "%teamcity.agents.linux%", "display" to "prompt")
            }

            // Only run on agents that don't have the "linux-initialized" tag
            requirements {
                doesNotExist("agent.linux-initialized")
                matches("teamcity.agent.jvm.os.name", "Linux")
            }

            steps {
                script {
                    name = "Initialize Linux Agent"
                    scriptContent = """
                        # Linux agent initialization script
                        # TODO: Add specific initialization commands for Linux
                        
                        # After successful initialization, add tag to agent
                        echo "##teamcity[setParameter name='agent.linux-initialized' value='true']"
                        echo "Linux agent initialization completed successfully"
                    """.trimIndent()
                }
            }
        }
        admin.buildType(linuxAgent)

        val windowsAgent = BuildType {
            id("${idp}_BT_Agent_Init_Windows")
            name = "Agent Init: Windows"

            params {
                param("agent", "", type = "select", "data_1" to "%teamcity.agents.windows%", "display" to "prompt")
            }

            // Only run on agents that don't have the "windows-initialized" tag
            requirements {
                doesNotExist("agent.windows-initialized")
                matches("teamcity.agent.jvm.os.name", "Windows*")
            }

            steps {
                script {
                    name = "Initialize Windows Agent"
                    scriptContent = """
                        REM Windows agent initialization script
                        REM TODO: Add specific initialization commands for Windows
                        
                        REM After successful initialization, add tag to agent
                        echo ##teamcity[setParameter name='agent.windows-initialized' value='true']
                        echo Windows agent initialization completed successfully
                    """.trimIndent()
                }
            }
        }
        admin.buildType(windowsAgent)
    }
}

// Register on load
@Suppress("unused")
private val __register = ProjectRegistry.register(Configurator)
