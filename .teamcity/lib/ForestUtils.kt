package lib

import jetbrains.buildServer.configs.kotlin.Template

data class TemplateRule(val pattern: String, val template: Template)

data class Node(
    val id: String,
    val name: String,
    val children: LinkedHashMap<String, Node> = linkedMapOf(),
    val leaves: MutableList<String> = mutableListOf()
)

fun safeId(s: String): String {
    val t = s.replace(Regex("[^A-Za-z0-9_]"), "_")
    return if (t.firstOrNull()?.isLetter() == true) t else "X_$t"
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

