package lib

import jetbrains.buildServer.configs.kotlin.Project
import projects.configureMainProject as configureMainProjectImpl
import projects.configureAdminProject as configureAdminProjectImpl

// Unified entrypoints to configure subprojects under the root
fun configureMainProject(root: Project) = configureMainProjectImpl(root)
fun configureAdminProject(root: Project) = configureAdminProjectImpl(root)
