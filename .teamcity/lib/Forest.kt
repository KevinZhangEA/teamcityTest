package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.triggers.schedule

/**
 * 用“路径清单 + 规则”生成整片森林。
 * - branches：分支列表（每个分支一棵树）
 * - leafPaths：所有叶子路径
 * - rules：路径到模板的匹配规则
 * - defaultTpl：未命中规则时的兜底模板
 * - vcsRoot：统一的 VCS root
 */
fun buildForestFromPaths(
    root: Project,
    idp: String,
    branches: List<String>,
    leafPaths: List<String>,
    rules: List<TemplateRule>,
    defaultTpl: Template,
    vcsRoot: VcsRoot
) {
    val roots = buildTreeFromPaths(leafPaths)

    branches.forEach { branch ->
        val prjBranch = Project {
            id("${idp}_Prj_$branch")
            name = branch
            params { param("BRANCH", branch) }
        }
        root.subProject(prjBranch)

        val composite = BuildType {
            id("${idp}_BT_${branch}_Composite")
            name = "00_🚪 ENTRANCE (Composite)"
            type = BuildTypeSettings.Type.COMPOSITE
            vcs {
                root(vcsRoot)
                branchFilter = "+:%BRANCH%"
            }
            triggers {
                vcs {
                    branchFilter = "+:%BRANCH%"
                    watchChangesInDependencies = true
                }
            }
            // 夜间定时（示例：03:00；如不需要可删除）
            triggers {
                schedule {
                    schedulingPolicy = daily { hour = 3; minute = 0 }
                    branchFilter = "+:%BRANCH%"
                    withPendingChangesOnly = false
                    enforceCleanCheckout = true
                    buildParams {
                        param("RUN_MODE", "nightly")
                        param("CLEAN_BUILD", "true")
                    }
                }
            }
        }
        prjBranch.buildType(composite)

        val dispatcher = BuildType {
            id("${idp}_BT_${branch}_Dispatcher")
            name = "01_📦 DISPATCHER"
            templates(defaultTpl)
        }
        prjBranch.buildType(dispatcher)

        data class Built(val allLeaves: List<BuildType>, val fin: BuildType)

        fun buildGroup(parent: Project, pathIds: List<String>, node: Node): Built {
            val groupPathId = (pathIds + node.id).joinToString("_")
            val prj = Project { id("${idp}_Prj_${branch}_$groupPathId"); name = node.name }
            parent.subProject(prj)

            val leaves = node.leaves.map { leafKey ->
                BuildType().also { bt ->
                    bt.id("${idp}_BT_${branch}_${groupPathId}_${safeId(leafKey)}")
                    bt.name = "${branch}_${groupPathId}_$leafKey"
                    bt.templates(pickTpl(groupPathId, leafKey, rules, defaultTpl))
                    bt.params {
                        param("GROUP_PATH", groupPathId)
                        param("LEAF_KEY",   leafKey)
                    }
                    bt.dependencies {
                        snapshot(dispatcher) { synchronizeRevisions = true }
                    }
                    prj.buildType(bt)
                }
            }

            val kids = node.children.values.map { child ->
                buildGroup(prj, pathIds + node.id, child)
            }

            val fin = BuildType {
                id("${idp}_BT_${branch}_${groupPathId}_finalize")
                name = "99_🏁 ${groupPathId}_finalize"
                templates(pickTpl(groupPathId, null, rules, defaultTpl))
                dependencies {
                    leaves.forEach { ch ->
                        snapshot(ch) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                        artifacts(ch) { artifactRules = "** => inputs/${ch.name}/" }
                    }
                    kids.forEach { b ->
                        snapshot(b.fin) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                        artifacts(b.fin) { artifactRules = "** => inputs/${b.fin.name}/" }
                    }
                }
            }
            prj.buildType(fin)
            return Built(leaves + kids.flatMap { it.allLeaves }, fin)
        }

        val builtRoots = roots.values.map { buildGroup(prjBranch, emptyList(), it) }

        composite.dependencies {
            snapshot(dispatcher) { synchronizeRevisions = true }
            builtRoots.forEach { b ->
                b.allLeaves.forEach { snapshot(it) { synchronizeRevisions = true } }
                snapshot(b.fin) { synchronizeRevisions = true }
            }
        }
    }
}
