import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.buildSteps.script

version = "2024.03"

project {

    // 统一前缀，确保创建“全新对象”
    val IDP = "v2"

    val tplDemo = Template {
        id("${IDP}_tpl_demo")
        name = "tpl-demo"
    
        // 给业务参数提供默认值，避免隐式 requirement
        params {
            param("CLIENT", "")
            param("CLIENT_CONFIG", "")
            param("SERVER", "")
            param("TOOLS", "")
            param("ASSET_GROUP", "")
            param("ASSET", "")
        }
    
        steps {
            // Unix / macOS
            script {
                name = "Hello + Produce artifact (Unix)"
                conditions { doesNotContain("teamcity.agent.jvm.os.name", "Windows") }
                scriptContent = """
                    set -e
                    echo "helloworld"
                    mkdir -p out
                    {
                      echo "buildConf: %teamcity.buildConfName%"
                      echo "buildId: %teamcity.build.id%"
                      echo "buildNumber: %build.number%"
                      echo "branch: %teamcity.build.branch%"
                      echo "clientConfig: %CLIENT_CONFIG%"
                      echo "agentName: %teamcity.agent.name%"
                      echo "agentOs: %teamcity.agent.jvm.os.name%"
                      date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ
                    } > out/output.txt
                """.trimIndent()
            }
    
            // Windows
            script {
                name = "Hello + Produce artifact (Windows)"
                conditions { contains("teamcity.agent.jvm.os.name", "Windows") }
                scriptContent = """
                    echo helloworld
                    if not exist out mkdir out
                    (
                      echo buildConf: %teamcity.buildConfName%
                      echo buildId: %teamcity.build.id%
                      echo buildNumber: %build.number%
                      echo branch: %teamcity.build.branch%
                      echo clientConfig: %CLIENT_CONFIG%
                      echo agentName: %teamcity.agent.name%
                      echo agentOs: %teamcity.agent.jvm.os.name%
                    ) > out\output.txt
                """.trimIndent()
            }
        }
    
        // 统一发布工件
        artifactRules = "out/**"
    }


    // 注册模板
    template(tplDemo)

    // 维度（这里只用于命名与层级，不再绑定到 VCS）
    val branches      = listOf("branch1", "branch2")
    val clients       = listOf("ios", "android")
    val clientConfigs = listOf("Debug", "Retail")
    val servers       = listOf("game", "relay", "blaze", "stargate")
    val tools         = listOf("tools", "tools1", "tools2", "tools3")
    val assetsUiChildren = listOf("textures", "layouts", "localization", "fonts", "videos")
    val assetsSiblings   = listOf("scripts", "players", "stadium", "cinamatics", "audio", "designconfigs")

    branches.forEach { br ->

        // 分支子项目
        val prjBranch = Project { id("${IDP}_Prj_$br"); name = br }

        // 入口：Composite（看板/入口，无触发器；先手动 Run）
        val composite = BuildType {
            id("${IDP}_BT_${br}_Composite")
            name = "${br} - Composite (Entrance/Board)"
            type = BuildTypeSettings.Type.COMPOSITE
        }
        prjBranch.buildType(composite)

        // Dispatcher（所有叶子 Job 的上游）
        val dispatcher = BuildType {
            id("${IDP}_BT_${br}_Dispatcher")
            name = "${br}_dispatcher"
            templates(tplDemo)
        }
        prjBranch.buildType(dispatcher)

        // ================== CLIENT 层 ==================
        val prjClient = Project { id("${IDP}_Prj_${br}_client"); name = "client" }
        prjBranch.subProject(prjClient)

        val clientBuilds = mutableListOf<BuildType>()
        clients.forEach { client ->
            clientConfigs.forEach { cfg ->
                val bt = BuildType {
                    id("${IDP}_BT_${br}_client_${client}_${cfg}")
                    name = "${br}_${client}_${cfg.lowercase()}"
                    templates(tplDemo)
                    params {
                        param("CLIENT", client)
                        param("CLIENT_CONFIG", cfg)
                    }
                    dependencies {
                        snapshot(dispatcher) { synchronizeRevisions = true } // 先跑 Dispatcher
                    }
                }
                prjClient.buildType(bt)
                clientBuilds += bt
            }
        }

        // Client Finalize：等待所有 client 子任务完成
        val finalizeClient = BuildType {
            id("${IDP}_BT_${br}_client_finalize")
            name = "${br}_client_finalize"
            templates(tplDemo)
            dependencies {
                clientBuilds.forEach { child ->
                    snapshot(child) {
                        synchronizeRevisions = true
                        onDependencyFailure = FailureAction.ADD_PROBLEM
                    }
                    artifacts(child) {
                        // 默认规则 sameChainOrLastFinished，无需显式设置
                        artifactRules = "** => inputs/${child.name}/"
                    }
                }
            }
        }
        prjClient.buildType(finalizeClient)

        // ================== SERVER 层 ==================
        val prjServer = Project { id("${IDP}_Prj_${br}_server"); name = "server" }
        prjBranch.subProject(prjServer)

        val serverBuilds = servers.map { srv ->
            BuildType {
                id("${IDP}_BT_${br}_server_${srv}")
                name = "${br}_server_${srv}"
                templates(tplDemo)
                params { param("SERVER", srv) }
                dependencies {
                    snapshot(dispatcher) { synchronizeRevisions = true }
                }
            }.also { prjServer.buildType(it) }
        }

        val finalizeServer = BuildType {
            id("${IDP}_BT_${br}_server_finalize")
            name = "${br}_server_finalize"
            templates(tplDemo)
            dependencies {
                serverBuilds.forEach { child ->
                    snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(child) {
                        artifactRules = "** => inputs/${child.name}/"
                    }
                }
            }
        }
        prjServer.buildType(finalizeServer)

        // ================== TOOLS 层 ==================
        val prjTools = Project { id("${IDP}_Prj_${br}_tools"); name = "tools" }
        prjBranch.subProject(prjTools)

        val toolsBuilds = tools.map { t ->
            BuildType {
                id("${IDP}_BT_${br}_tools_${t}")
                name = "${br}_$t"
                templates(tplDemo)
                params { param("TOOLS", t) }
                dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            }.also { prjTools.buildType(it) }
        }

        val finalizeTools = BuildType {
            id("${IDP}_BT_${br}_tools_finalize")
            name = "${br}_tools_finalize"
            templates(tplDemo)
            dependencies {
                toolsBuilds.forEach { child ->
                    snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(child) {
                        artifactRules = "** => inputs/${child.name}/"
                    }
                }
            }
        }
        prjTools.buildType(finalizeTools)

        // ================== ASSETS 层 ==================
        val prjAssets = Project { id("${IDP}_Prj_${br}_assets"); name = "assets" }
        prjBranch.subProject(prjAssets)

        // --- assets/ui 子树 ---
        val prjAssetsUi = Project { id("${IDP}_Prj_${br}_assets_ui"); name = "ui" }
        prjAssets.subProject(prjAssetsUi)

        // ui 的叶子构建
        val uiLeafBuilds = assetsUiChildren.map { child ->
            BuildType {
                id("${IDP}_BT_${br}_assets_ui_${child}")
                name = "${br}_assets_${child}"
                templates(tplDemo)
                params { param("ASSET_GROUP", "ui"); param("ASSET", child) }
                dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            }.also { prjAssetsUi.buildType(it) }
        }

        // UI Finalize：等待其子叶子完成
        val finalizeUi = BuildType {
            id("${IDP}_BT_${br}_assets_ui_finalize")
            name = "${br}_assets_ui_finalize"
            templates(tplDemo)
            dependencies {
                uiLeafBuilds.forEach { child ->
                    snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(child) {
                        artifactRules = "** => inputs/${child.name}/"
                    }
                }
            }
        }
        prjAssetsUi.buildType(finalizeUi)

        // assets 的与 ui 平级的兄弟叶子
        val assetsSiblingBuilds = assetsSiblings.map { a ->
            BuildType {
                id("${IDP}_BT_${br}_assets_${a}")
                name = "${br}_assets_${a}"
                templates(tplDemo)
                params { param("ASSET", a) }
                dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            }.also { prjAssets.buildType(it) }
        }

        // Assets Finalize：等待 UI Finalize + 其它叶子完成
        val finalizeAssets = BuildType {
            id("${IDP}_BT_${br}_assets_finalize")
            name = "${br}_assets_finalize"
            templates(tplDemo)
            dependencies {
                snapshot(finalizeUi) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                artifacts(finalizeUi) {
                    artifactRules = "** => inputs/${finalizeUi.name}/"
                }
                assetsSiblingBuilds.forEach { leaf ->
                    snapshot(leaf) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                    artifacts(leaf) {
                        artifactRules = "** => inputs/${leaf.name}/"
                    }
                }
            }
        }
        prjAssets.buildType(finalizeAssets)

        // ================== Composite 汇总（纯看板） ==================
        composite.dependencies {
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

        // 把分支项目挂到根项目下（注意：是 subProject）
        subProject(prjBranch)
    }
}
