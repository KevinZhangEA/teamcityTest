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
          G(
            id = "client", name = "client",
            leaves = listOf(
              L("ios_debug"), L("ios_retail"),
              L("android_debug"), L("android_retail")
            )
          ),
          G(
            id = "server", name = "server",
            leaves = listOf(L("game"), L("relay"), L("blaze"), L("stargate"))
          ),
          G(
            id = "tools", name = "tools",
            leaves = listOf(L("tools"), L("tools1"), L("tools2"), L("tools3"))
          ),
          G(
            id = "assets", name = "assets",
            leaves = listOf(L("scripts"), L("players"), L("stadium"), L("cinamatics"), L("audio"), L("designconfigs")),
            groups = listOf(
              G(id = "ui", name = "ui",
                leaves = listOf(L("textures"), L("layouts"), L("localization"), L("fonts"), L("videos"))
              )
            )
          )
        )
    )

    // 一行生成整片森林（不写任何业务分支判断）
    buildForest(this, idp, tpl, spec)
}
