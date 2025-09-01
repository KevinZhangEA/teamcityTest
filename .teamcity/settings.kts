import jetbrains.buildServer.configs.kotlin.*
import lib.configureAdminProject
import lib.configureMainProject

version = "2025.07"

project {
    // Root-level VCS secure parameters
    params {
        password("secure.P4USER", "")
        password("secure.P4PORT", "")
        password("secure.P4PASSWD", "")
    }

    configureAdminProject(this)
    configureMainProject(this)
}
