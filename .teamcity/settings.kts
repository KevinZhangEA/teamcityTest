import jetbrains.buildServer.configs.kotlin.*
import lib.configureProjectsByClassNames
import projects.ProjectList

version = "2025.07"

project {
    // Root-level VCS secure parameters
    params {
        password("secure.P4USER", "")
        password("secure.P4PORT", "")
        password("secure.P4PASSWD", "")
    }

    // Configure desired projects from the central list
    configureProjectsByClassNames(this, ProjectList.enabled)
}
