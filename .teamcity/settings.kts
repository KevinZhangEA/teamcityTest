import jetbrains.buildServer.configs.kotlin.*
import lib.demoTemplate
import lib.buildForestFromPaths

version = "2024.03"

project {
    val tpl = demoTemplate("v2_tpl_demo")
    template(tpl)

    val branches  = listOf("branch1", "branch2")
    val leafPaths = listOf(
        // client
        "client/ios_debug", "client/ios_retail",
        "client/android_debug", "client/android_retail",
        // server
        "server/game", "server/relay", "server/blaze", "server/stargate",
        // tools
        "tools/tools", "tools/tools1", "tools/tools2", "tools/tools3",
        // assets
        "assets/scripts", "assets/players", "assets/stadium", "assets/cinamatics",
        "assets/audio", "assets/designconfigs",
        // assets/ui 子组
        "assets/ui/textures", "assets/ui/layouts", "assets/ui/localization", "assets/ui/fonts", "assets/ui/videos"
    )

    // 一行生成（不改文件名，仅改内容）
    buildForestFromPaths(this, idp, tpl, branches, leafPaths)
}
