package projects.mainproject.templates

@Suppress("unused")
// Per-mainproject list of TemplateProvider Kotlin object FQCNs
object TemplateList {
    val enabled: List<String> = listOf(
        "projects.mainproject.templates.DefaultTemplateProvider",
        "projects.mainproject.templates.ClientIosTemplateProvider",
        "projects.mainproject.templates.ClientAndroidTemplateProvider",
        "projects.mainproject.templates.ServerTemplateProvider",
        "projects.mainproject.templates.ToolsTemplateProvider",
        "projects.mainproject.templates.AssetsTemplateProvider"
    )
}
