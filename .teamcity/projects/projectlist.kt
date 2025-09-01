package projects

// Central place to declare which project configurators are enabled.
object ProjectList {
    // Fully qualified Kotlin object names implementing lib.ProjectConfigurator
    val enabled: List<String> = listOf(
        "projects.adminproject.Configurator",
        "projects.mainproject.Configurator"
    )
}

