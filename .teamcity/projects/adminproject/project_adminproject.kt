package projects.adminproject

import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.script

// Configure only the Admin project (as a sibling subproject under root)
fun configureAdminProject(root: Project) {
    val idp = "v2"

    val admin = Project {
        id("${idp}_Prj_Admin")
        name = "Admin"
    }
    root.subProject(admin)

    val util = BuildType {
        id("${idp}_BT_Admin_Utilities")
        name = "Admin: Utilities"
        steps {
            script {
                name = "noop"
                scriptContent = """
                    set -e
                    echo "admin utilities placeholder"
                """.trimIndent()
            }
        }
    }
    admin.buildType(util)
}
