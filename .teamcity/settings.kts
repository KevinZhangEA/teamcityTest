import jetbrains.buildServer.configs.kotlin.*
import lib.*
import lib.TemplateRule as R
import lib.buildForestFromPaths

version = "2025.07"

project {
    val idp = "v2"

    // 使用系统配置的 VCS root（单一根 VCS）
    val vcsRoot = DslContext.settingsRoot

    // 在根项目预定义 UI 侧需要设置的参数占位（不提供值，便于在 UI 中集中设置）
    params {
        // Perforce（全部使用 secure 参数占位，避免 DSL 覆盖 UI）
        password("secure.P4USER", "")
        password("secure.P4PORT", "")
        password("secure.P4PASSWD", "")
    }

    // 六套模板（指定各自的 P4 stream）
    val tplDefault = defaultTemplate("${idp}_tpl_default", vcsRoot, "//streams/default")
    val tplCliIOS  = clientIosTemplate("${idp}_tpl_client_ios", vcsRoot, "//streams/client-ios")
    val tplCliAnd  = clientAndroidTemplate("${idp}_tpl_client_android", vcsRoot, "//streams/client-android")
    val tplServer  = serverTemplate("${idp}_tpl_server", vcsRoot, "//streams/server")
    val tplTools   = toolsTemplate("${idp}_tpl_tools", vcsRoot, "//streams/tools")
    val tplAssets  = assetsTemplate("${idp}_tpl_assets", vcsRoot, "//streams/assets")

    // 注册模板
    listOf(tplDefault, tplCliIOS, tplCliAnd, tplServer, tplTools, tplAssets).forEach { template(it) }

    // 路径清单（叶子）
    val branches  = listOf("branch1", "branch2")
    val leafPaths = listOf(
        // client
        "client/ios/debug", "client/ios/retail",
        "client/android/debug", "client/android/retail",
        // server
        "server/game", "server/relay", "server/blaze", "server/stargate",
        // tools
        "tools/tools", "tools/tools1", "tools/tools2", "tools/tools3",
        // assets
        "assets/scripts", "assets/players", "assets/stadium", "assets/cinematics",
        "assets/audio", "assets/designconfigs",
        // assets/ui
        "assets/ui/textures", "assets/ui/layouts", "assets/ui/localization", "assets/ui/fonts", "assets/ui/videos"
    )

    // 规则（先命中先用）；finalize 会用 "groupPath/_finalize" 来参与匹配
    val rules = listOf(
        R("client/ios/**",      tplCliIOS),
        R("client/android/**",  tplCliAnd),
        R("server/**",          tplServer),
        R("tools/**",           tplTools),
        R("assets/**",          tplAssets)
    )

    // 生成整片森林（Composite 已内置 VCS 增量触发 + 夜间 Clean 重编译）
    buildForestFromPaths(
        root       = this,
        idp        = idp,
        branches   = branches,
        leafPaths  = leafPaths,
        rules      = rules,
        defaultTpl = tplDefault,
        vcsRoot    = vcsRoot
    )
}
