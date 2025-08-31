package lib

import jetbrains.buildServer.configs.kotlin.*

data class TemplateRule(val pattern: String, val template: Template)

data class Node(
    val id: String,
    val name: String,
    val children: LinkedHashMap<String, Node> = linkedMapOf(),
    val leaves: MutableList<String> = mutableListOf()
)

data class Built(val allLeaves: List<BuildType>, val fin: BuildType)

/**
 * 递归构建组和叶子节点
 */
fun buildGroup(
    parent: Project,
    pathIds: List<String>,
    node: Node,
    idp: String,
    branch: String,
    dispatcher: BuildType,
    rules: List<TemplateRule>,
    defaultTpl: Template
): Built {
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
        buildGroup(prj, pathIds + node.id, child, idp, branch, dispatcher, rules, defaultTpl)
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

// 极简 glob：支持 "**"（跨段）与 "*"（单段）
fun globMatch(pattern: String, path: String): Boolean {
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

// 构建树结构
fun buildTreeFromPaths(leafPaths: List<String>): LinkedHashMap<String, Node> {
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
    return roots
}

// 模板选择
fun pickTpl(
    groupPathId: String,
    leafKey: String?,
    rules: List<TemplateRule>,
    defaultTpl: Template
): Template {
    val logical = if (leafKey == null)
        groupPathId.replace('_','/') + "/_finalize"
    else
        groupPathId.replace('_','/') + "/$leafKey"
    return rules.firstOrNull { globMatch(it.pattern, logical) }?.template ?: defaultTpl
}

fun safeId(s: String): String {
    val t = s.replace(Regex("[^A-Za-z0-9_]"), "_")
    return if (t.firstOrNull()?.isLetter() == true) t else "X_$t"
}
