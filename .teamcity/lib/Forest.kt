package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings

// 规则：用路径模式选择模板（pattern 支持 "*" 与 "**"）
data class TemplateRule(val pattern: String, val template: Template)

private data class Node(val id: String, val name: String) {
    val children = linkedMapOf<String, Node>()
    val leaves   = mutableListOf<String>()
}

private fun safeId(s: String): String {
    val t = s.replace(Regex("[^A-Za-z0-9_]"), "_")
    return if (t.firstOrNull()?.isLetter() == true) t else "X_$t"
}

// 极简 glob：支持 "**"（跨段）与 "*"（单段）
private fun globMatch(pattern: String, path: String): Boolean {
    fun split(s: String) = s.split('/').filter { it.isNotBlank() }
    val p = split(pattern); val x = split(path)
    fun dfs(i: Int, j: Int): Boolean = when {
        i == p.size && j == x.size -> true
        i == p.size || j > x.size  -> false
        p[i] == "**"               -> (j..x.size).any { dfs(i + 1, it) }
        j == x.size                -> false
        else -> {
            val ok = (p[i] == "*") || (p[i] == x[j])
            ok && dfs(i + 1, j + 1)
        }
    }
    return dfs(0, 0)
}

/**
 * 用“路径清单 + 规则”生成整片森林。
 * - branches：分支列表（每个分支一棵树）
 * - leafPaths：所有叶子路径，如 "client/ios/debug", "server/game", "assets/ui/fonts"
 * - rules：路径到模板的匹配规则（按顺序，先命中先用）
 * - defaultTpl：未命中规则时的兜底模板；Dispatcher/Composite 也使用该模板
 */
fun buildForestFromPaths(
    root: Project,
    idp: String,
    branches: List<String>,
    leafPaths: List<String>,
    rules: List<TemplateRule>,
    defaultTpl: Template
) {
    // 组树装配
    val roots = linkedMapOf<String, Node>()
    fun ensure(segs: List<String>): Node {
        var n = roots.getOrPut(segs.first()) { Node(safeId(segs.first()), segs.first()) }
        for (s in segs.drop(1)) n = n.children.getOrPut(s) { Node(safeId(s), s) }
        return n
    }
    leafPaths.forEach { p ->
        val segs = p.split('/').filter { it.isNotBlank() }
        require(segs.size >= 2) { "Leaf path must be 'group/.../leaf', got: $p" }
        ensure(segs.dropLast(1)).leaves += segs.last()
    }

    // 依据路径选择模板（finalize 用 groupPath/_finalize 参与匹配）
    fun pickTpl(groupPathId: String, leafKey: String?): Template {
        val logical = if (leafKey == null)
            groupPathId.replace('_','/') + "/_finalize"
        else
            groupPathId.replace('_','/') + "/$leafKey"
        return rules.firstOrNull { globMatch(it.pattern, logical) }?.template ?: defaultTpl
    }

    branches.forEach { br ->
        // 分支根
        val prjBranch = Project { id("${idp}_Prj_$br"); name = br }
        root.subProject(prjBranch)

        // 入口 & 分发（看板/起链）
        val composite = BuildType {
            id("${idp}_BT_${br}_Composite")
            name = "00_🚪 ENTRANCE (Composite)"
            type = BuildTypeSettings.Type.COMPOSITE
        }
        val dispatcher = BuildType {
            id("${idp}_BT_${br}_Dispatcher")
            name = "01_📦 DISPATCHER"
            templates(defaultTpl)
        }
        prjBranch.buildType(composite); prjBranch.buildType(dispatcher)

        data class Built(val allLeaves: List<BuildType>, val fin: BuildType)

        fun buildGroup(parent: Project, pathIds: List<String>, node: Node): Built {
            val groupPathId = (pathIds + node.id).joinToString("_")
            val prj = Project { id("${idp}_Prj_${br}_$groupPathId"); name = node.name }
            parent.subProject(prj)

            // 叶子
            val leaves = node.leaves.map { leafKey ->
                BuildType().also { bt ->
                    bt.id("${idp}_BT_${br}_${groupPathId}_${safeId(leafKey)}")
                    bt.name = "${br}_${groupPathId}_$leafKey"
                    bt.templates(pickTpl(groupPathId, leafKey))
                    bt.params {
                        param("GROUP_PATH", groupPathId)
                        param("LEAF_KEY",   leafKey)
                    }
                    bt.dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
                    prj.buildType(bt)
                }
            }

            // 子组
            val kids = node.children.values.map { child ->
                buildGroup(prj, pathIds + node.id, child)
            }

            // finalize（等本组叶子 + 子组 finalize）
            val fin = BuildType {
                id("${idp}_BT_${br}_${groupPathId}_finalize")
                name = "99_🏁 ${groupPathId}_finalize"
                templates(pickTpl(groupPathId, null))
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

        // Composite 仅用作链路看板
        composite.dependencies {
            snapshot(dispatcher) { synchronizeRevisions = true }
            builtRoots.forEach { b ->
                b.allLeaves.forEach { snapshot(it) { synchronizeRevisions = true } }
                snapshot(b.fin) { synchronizeRevisions = true }
            }
        }
    }
}
