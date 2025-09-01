package lib

import jetbrains.buildServer.configs.kotlin.Project

// Registry-based API to avoid hardcoding per-project imports
interface ProjectConfigurator {
    val name: String
    fun configure(root: Project)
}

object ProjectRegistry {
    private val configurators = linkedMapOf<String, ProjectConfigurator>()
    fun register(c: ProjectConfigurator) { configurators[c.name] = c }
    fun all(): List<ProjectConfigurator> = configurators.values.toList()
    fun byNames(names: List<String>): List<ProjectConfigurator> = names.mapNotNull { configurators[it] }
}

// Reflective loader: configure projects by FQCN of Kotlin objects implementing ProjectConfigurator
fun configureProjectsByClassNames(root: Project, classNames: List<String>) {
    classNames.forEach { fqcn ->
        val inst = try {
            val clazz = Class.forName(fqcn)
            val instanceField = try { clazz.getField("INSTANCE").get(null) } catch (e: NoSuchFieldException) { null }
            when (instanceField) {
                is ProjectConfigurator -> instanceField
                else -> clazz.kotlin.objectInstance as? ProjectConfigurator
            }
        } catch (e: Throwable) {
            null
        }
        require(inst != null) { "Cannot load ProjectConfigurator for $fqcn" }
        (inst as ProjectConfigurator).configure(root)
    }
}
