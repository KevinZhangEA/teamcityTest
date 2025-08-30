import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

version = "2024.03"

project {

    // --- 通用模板：演示脚本 ---
    val tplDemo = Template {
        id("tpl_demo")
        name = "tpl-demo"
        steps {
            script {
                name = "Hello"
                scriptContent = """echo "helloworld""""
            }
        }
        // 演示：不设 agent 要求 / 不产出真实工件
        // 如果你要让 Artifact 依赖有文件，可在这里额外 echo 到某个文件并设置 artifactRules
    }

    // 维度
    val branches      = listOf("branch1", "branch2")
    val clients       = listOf("ios", "android")
    val clientConfigs = listOf("Debug", "Retail")
    val servers       = listOf("game", "relay", "blaze", "stargate")
    val tools         = listOf("tools", "tools1", "tools2", "tools3")
    val assetsUiChildren = listOf("textures", "layouts", "localization", "fonts", "videos")
    val assetsSiblings   = listOf("scripts", "players", "stadium", "cinamatics", "audio", "designconfigs")

    branches.forEach { br ->

        // 分支子项目
        val prjBranch = Project { id("Prj_$br"); name = br }

        // 入口：Composite（看板/触发入口）
        val composite = BuildType {
            id("BT_${br}_Composite")
            name = "${br} - Composite (Entrance/Board)"
            type = BuildTypeSettings.Type.COMPOSITE
            // 触发器（演示）：只监听该分支
            triggers {
                vcs { branchFilter = "+:refs/heads/$br" }
            }
        }
        prjBranch.buildType(composite)

        // Dispatcher（所有叶子 Job 都 snapshot 依赖它）
        val dispatcher = BuildType {
            id("BT_${br}_Dispatcher")
            name = "${br}_dispatcher"
            templates(tplDemo)
            vcs { root(DslContext.settingsRoot); branchFilter = "+:refs/heads/$br" }
            triggers { /* 入口统一在 Composite；这里不放触发器 */ }
        }
        prjBranch.buildType(dispatcher)

        // ================== CLIENT 层 ==================
        val prjClient = Project { id("Prj_${br}_client"); name = "client" }
        prjBranch.subProject(prjClient)

        val clientBuilds = mutableListOf<BuildType>()
        clients.forEach { client ->
            clientConfigs.forEach { cfg ->
                val bt = BuildType {
                    id("BT_${br}_client_${client}_${cfg}")
                    name = "${br}_${client}_${cfg.lowercase()}"
                    templates(tplDemo)
                    params {
                        param("CLIENT", client)
                        param("CLIENT_CONFIG", cfg)
                    }
                    vcs { root(DslContext.settingsRoot); branchFilter = "+:refs/heads/$br" }
                    dependencies {
                        snapshot(dispatcher) { synchronizeRevisions = true } // 顺序：先 Dispatcher
                    }
                }
                prjClient.buildType(bt)
                clientBuilds += bt
            }
        }

        // Client Finalize：等子 Job 都完成后再完成
        val finalizeClient = BuildType {
            id("BT_${br}_client_finalize")
            name = "${br}_client_finalize"
            templates(tplDemo)
            dependencies {
                clientBuilds.forEach { child ->
                    snapshot(child) {
                        synchronizeRevisions = true
                        onDependencyFailure = FailureAction.ADD_PROBLEM // 子失败也可运行做汇总；按需改为 CANCEL
                    }
                    // 示例：Artifact 依赖占位（建议 UI 勾“Build from the same chain”）
                    artifacts(child) {
                        artifactRules = "** => inputs/${child.name}/"
                        // cleanDestination = true
                        // （同链获取可在 UI 勾选；或在 DSL 里设置相应 build rule）
                    }
                }
            }
        }
        prjClient.buildType(finalizeClient)

        // ================== SERVER 层 ==================
        val prjServer = Project { id("Prj_${br}_server"); name = "server" }
        prjBranch.subProject(prjServer)

        val serverBuilds = servers.map { srv ->
            BuildType {
                id("BT_${br}_server_${srv}")
                name = "${br}_server_${srv}"
                templates(tplDemo)
                params { param("SERVER", srv) }
                vcs { root(DslContext.settingsRoot); branchFilter = "+:refs/heads/$br" }
                dependencies {
                    snapshot(dispatcher) { synchronizeRevisions = true }
                }
            }.also { prjServer.buildType(it) }
        }

        val finalizeServer = BuildType {
            id("BT_${br}_server_finalize")
            name = "${br}_server_finalize"
            templates(tplDemo)
            dependencies {
                serverBuilds.forEach { child ->
                    snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(child) { artifactRules = "** => inputs/${child.name}/" }
                }
            }
        }
        prjServer.buildType(finalizeServer)

        // ================== TOOLS 层 ==================
        val prjTools = Project { id("Prj_${br}_tools"); name = "tools" }
        prjBranch.subProject(prjTools)

        val toolsBuilds = tools.map { t ->
            BuildType {
                id("BT_${br}_tools_${t}")
                name = "${br}_$t"
                templates(tplDemo)
                params { param("TOOLS", t) }
                vcs { root(DslContext.settingsRoot); branchFilter = "+:refs/heads/$br" }
                dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            }.also { prjTools.buildType(it) }
        }

        val finalizeTools = BuildType {
            id("BT_${br}_tools_finalize")
            name = "${br}_tools_finalize"
            templates(tplDemo)
            dependencies {
                toolsBuilds.forEach { child ->
                    snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(child) { artifactRules = "** => inputs/${child.name}/" }
                }
            }
        }
        prjTools.buildType(finalizeTools)

        // ================== ASSETS 层 ==================
        val prjAssets = Project { id("Prj_${br}_assets"); name = "assets" }
        prjBranch.subProject(prjAssets)

        // --- assets/ui 子树 ---
        val prjAssetsUi = Project { id("Prj_${br}_assets_ui"); name = "ui" }
        prjAssets.subProject(prjAssetsUi)

        // ui 的叶子构建
        val uiLeafBuilds = assetsUiChildren.map { child ->
            BuildType {
                id("BT_${br}_assets_ui_${child}")
                name = "${br}_assets_${child}"
                templates(tplDemo)
                params { param("ASSET_GROUP", "ui"); param("ASSET", child) }
                vcs { root(DslContext.settingsRoot); branchFilter = "+:refs/heads/$br" }
                dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            }.also { prjAssetsUi.buildType(it) }
        }

        // UI Finalize：等待其子叶子完成
        val finalizeUi = BuildType {
            id("BT_${br}_assets_ui_finalize")
            name = "${br}_assets_ui_finalize"
            templates(tplDemo)
            dependencies {
                uiLeafBuilds.forEach { child ->
                    snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(child) { artifactRules = "** => inputs/${child.name}/" }
                }
            }
        }
        prjAssetsUi.buildType(finalizeUi)

        // assets 的与 ui 平级的兄弟叶子
        val assetsSiblingBuilds = assetsSiblings.map { a ->
            BuildType {
                id("BT_${br}_assets_${a}")
                name = "${br}_assets_${a}"
                templates(tplDemo)
                params { param("ASSET", a) }
                vcs { root(DslContext.settingsRoot); branchFilter = "+:refs/heads/$br" }
                dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            }.also { prjAssets.buildType(it) }
        }

        // Assets Finalize：等待 UI Finalize + 其它叶子完成
        val finalizeAssets = BuildType {
            id("BT_${br}_assets_finalize")
            name = "${br}_assets_finalize"
            templates(tplDemo)
            dependencies {
                snapshot(finalizeUi) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                artifacts(finalizeUi) { artifactRules = "** => inputs/${finalizeUi.name}/" }
                assetsSiblingBuilds.forEach { leaf ->
                    snapshot(leaf) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(leaf) { artifactRules = "** => inputs/${leaf.name}/" }
                }
            }
        }
        prjAssets.buildType(finalizeAssets)

        // ================== 把所有节点挂到 Composite（漂亮链图） ==================
        composite.dependencies {
            // 入口直接 snapshot 所有节点，链图完整；执行顺序由各自下游 snapshot 决定
            snapshot(dispatcher) { synchronizeRevisions = true }

            // client
            clientBuilds.forEach { snapshot(it) { synchronizeRevisions = true } }
            snapshot(finalizeClient) { synchronizeRevisions = true }

            // server
            serverBuilds.forEach { snapshot(it) { synchronizeRevisions = true } }
            snapshot(finalizeServer) { synchronizeRevisions = true }

            // tools
            toolsBuilds.forEach { snapshot(it) { synchronizeRevisions = true } }
            snapshot(finalizeTools) { synchronizeRevisions = true }

            // assets
            uiLeafBuilds.forEach { snapshot(it) { synchronizeRevisions = true } }
            snapshot(finalizeUi) { synchronizeRevisions = true }
            assetsSiblingBuilds.forEach { snapshot(it) { synchronizeRevisions = true } }
            snapshot(finalizeAssets) { synchronizeRevisions = true }
        }

        // 注册分支子项目
        project(prjBranch)
    }
}
