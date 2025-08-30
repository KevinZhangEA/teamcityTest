import jetbrains.buildServer.configs.kotlin.*
import lib.*
import lib.GroupSpec as G
import lib.LeafSpec as L

version = "2024.03"

project {
    val idp = "v2"

    // 模板（Linux-only，产出 out/output.txt）
    val tpl = demoTemplate("${idp}_tpl_demo")
    template(tpl)

    // 纯配置：声明组与叶子。你可以随意增删组/层级/叶子与参数。
    val spec = Spec(
        branches = listOf("branch1", "branch2"),
        rootGroups = listOf(
            // 例：client 组（由配置决定有哪些 leaf）
            G(
                id = "client", name = "client",
                leaves = listOf(
                    L(key = "ios_debug",   displayName = "branch_client_ios_debug",   params = mapOf("platform" to "ios", "config" to "Debug")),
                    L(key = "ios_retail",  displayName = "branch_client_ios_retail",  params = mapOf("platform" to "ios", "config" to "Retail")),
                    L(key = "android_debug",  displayName = "branch_client_android_debug",  params = mapOf("platform" to "android", "config" to "Debug")),
                    L(key = "android_retail", displayName = "branch_client_android_retail", params = mapOf("platform" to "android", "config" to "Retail")),
                )
            ),
            // 例：server 组
            G(
                id = "server", name = "server",
                leaves = listOf(
                    L("game"), L("relay"), L("blaze"), L("stargate")
                )
            ),
            // 例：tools 组
            G(
                id = "tools", name = "tools",
                leaves = listOf(L("tools"), L("tools1"), L("tools2"), L("tools3"))
            ),
            // 例：assets 组（含一个 ui 子组 + 其它直接叶子）
            G(
                id = "assets", name = "assets",
                leaves = listOf( // 同级的“兄弟叶子”
                    L("scripts"), L("players"), L("stadium"), L("cinamatics"),
                    L("audio"), L("designconfigs")
                ),
                groups = listOf(
                    G(
                        id = "ui", name = "ui",
                        leaves = listOf(L("textures"), L("layouts"), L("localization"), L("fonts"), L("videos"))
                    )
                )
            )
        )
    )

    // 一行生成整片森林（不写任何业务分支判断）
    buildForest(this, idp, tpl, spec)
}
