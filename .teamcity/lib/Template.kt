package lib

import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.VcsRoot

// Registry-based API for Templates to avoid hardcoding imports in lib/Template.kt
interface TemplateProvider {
    // Unique key, e.g., "default", "client-ios", "server"
    val key: String
    fun create(id: String, vcsRoot: VcsRoot, p4Stream: String): Template
}

object TemplateRegistry {
    private val providers = linkedMapOf<String, TemplateProvider>()

    fun register(p: TemplateProvider) { providers[p.key] = p }

    fun all(): List<TemplateProvider> = providers.values.toList()

    fun byKeys(keys: List<String>): List<TemplateProvider> = keys.mapNotNull { providers[it] }

    fun get(key: String): TemplateProvider = providers[key]
        ?: error("TemplateProvider not found for key: $key. Registered: ${providers.keys}")

    // Reflective loader: load providers by FQCN of Kotlin objects implementing TemplateProvider
    fun loadProvidersByClassNames(classNames: List<String>) {
        classNames.forEach { fqcn ->
            val inst: TemplateProvider? = try {
                val clazz = Class.forName(fqcn)
                val instanceField = try { clazz.getField("INSTANCE").get(null) } catch (_: NoSuchFieldException) { null }
                when (instanceField) {
                    is TemplateProvider -> instanceField
                    else -> clazz.kotlin.objectInstance as? TemplateProvider
                }
            } catch (_: Throwable) { null }
            require(inst != null) { "Cannot load TemplateProvider for $fqcn" }
            register(inst)
        }
    }
}
