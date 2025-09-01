package projects.adminproject.config

object AdminConfig {
    // Project name and identifier prefix
    val name: String = "adminproject"
    val idp: String = "v2"

    // Admin subproject identifiers
    val adminProjectKey: String = "Admin"            // used in Project id suffix
    val adminProjectName: String = "Admin"           // visible name

    // Utilities build type identifiers and metadata
    val utilitiesBuildTypeKey: String = "Admin_Utilities"  // used in BuildType id suffix
    val utilitiesBuildTypeName: String = "Admin: Utilities"
    val utilitiesStepName: String = "noop"
    val utilitiesScript: String = """
        set -e
        echo "admin utilities placeholder"
    """.trimIndent()
}

