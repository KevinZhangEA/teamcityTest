package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings

/** 通用“分组/叶子”声明（保留旧接口，兼容按对象建树的用法） */
data class Spec(
    val branches: List<String>,
    val rootGroups: List<GroupSpec>
)
data class GroupSpec(
    val id: String,
    val name: String,
    val leaves: List<LeafSpec> = emptyList(),
    val groups: List<GroupSpec> = emptyList()
)
data class LeafSpec(
    val key: String,
    val displayName: String? = null,
    val params: Map<String,String> = emptyMap()
)

private data class BuiltGroup(val allLeaves: List<BuildType>, val finalize: BuildType)

/** 旧接口：按对象建树 */
fun buildForest(root: Project, idp: String, tpl: Template, spec: Spec) {
    spec.branches.forEach { br ->
        val prjBranch = Project { id("${idp}_Prj_$br"); name = br }
        root.subProject(prjBranch)

        val composite = BuildType {
            id("${idp}_BT_${br}_Composite")
            name = "00_🚪 ENTRANCE (Composite)"
            type = BuildTypeSettings.Type.COMPOSITE
        }
        val dispatcher = BuildType {
            id("${idp}_BT_${br}_Dispatcher")
            name = "01_📦 DISPATCHER"
            templates(tpl)
        }
        prjBranch.buildType(composite)
        prjBranch.buildType(dispatcher)

        fun buildGroup(parent: Project, path: String, g: GroupSpec): BuiltGroup {
            val groupPathId = if (path.isEmpty()) g.id else "${path}_${g.id}"
            val prj = Project { id("${idp}_Prj_${br}_$groupPathId"); name = g.name }
            parent.subProject(prj)

            val leaves = g.leaves.map { leaf ->
                BuildType().also { bt ->
                    bt.id("${idp}_BT_${br}_${groupPathId}_${leaf.key}")
                    bt.name = leaf.displayName ?: "${br}_${groupPathId}_${leaf.key}"
                    bt.templates(tpl)
                    bt.params {
                        param("GROUP_PATH", groupPathId)
                        param("LEAF_KEY",   leaf.key)
                        leaf.params.forEach { (k,v) -> param(k, v) }
                    }
                    bt.dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
                    prj.buildType(bt)
                }
            }

            val builtChildren = g.groups.map { sub -> buildGroup(prj, groupPathId, sub) }

            val fin = BuildType {
                id("${idp}_BT_${br}_${groupPathId}_finalize")
                name = "99_🏁 ${g.name}_finalize"
                templates(tpl)
                dependencies {
                    leaves.forEach { ch ->
                        snapshot(ch) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                        artifacts(ch) { artifactRules = "** => inputs/${ch.name}/" }
                    }
                    builtChildren.forEach { bc ->
                        snapshot(bc.finalize) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                        artifacts(bc.finalize) { artifactRules = "** => inputs/${bc.finalize.name}/" }
                    }
                }
            }
            prj.buildType(fin)

            return BuiltGroup(allLeaves = leaves + builtChildren.flatMap { it.allLeaves }, finalize = fin)
        }

        val builtRoots = spec.rootGroups.map { g -> buildGroup(prjBranch, "", g) }

        composite.dependencies {
            snapshot(dispatcher) { synchronizeRevisions = true }
            builtRoots.forEach { b ->
                b.allLeaves.forEach { snapshot(it) { synchronizeRevisions = true } }
                snapshot(b.finalize) { synchronizeRevisions = true }
            }
        }
    }
}

/** ★ 新接口：按“路径字符串”生成整片森林（不改变文件名，只新增函数） */
fun buildForestFromPaths(root: Project, idp: String, tpl: Template, branches: List<String>, leafPaths: List<String>) {
    // 把路径转为对象树，然后复用上面的 buildForest
    data class TmpNode(val id: String, val name: String) {
        val children = linkedMapOf<String, TmpNode>()
        val leaves = mutableListOf<String>()
    }
    fun safeId(s: String): String {
        val t = s.replace(Regex("[^A-Za-z0-9_]"), "_")
        return if (t.firstOrNull()?.isLetter() == true) t else "X_$t"
    }
    fun ensure(root: MutableMap<String,TmpNode>, segs: List<String>): TmpNode {
        var node = root.getOrPut(segs.first()) { TmpNode(safeId(segs.first()), segs.first()) }
        for (seg in segs.drop(1)) node = node.children.getOrPut(seg) { TmpNode(safeId(seg), seg) }
        return node
    }

    val map = linkedMapOf<String, TmpNode>()
    leafPaths.forEach { p ->
        val segs = p.split('/').filter { it.isNotBlank() }
        require(segs.size >= 2) { "leaf path must be 'group/.../leaf', got: $p" }
        val leaf = segs.last()
        val grp = ensure(map, segs.dropLast(1))
        grp.leaves += leaf
    }

    fun toGroupSpec(node: TmpNode): GroupSpec =
        GroupSpec(
            id = node.id,
            name = node.name,
            leaves = node.leaves.map { LeafSpec(key = safeId(it)) },
            groups = node.children.values.map { toGroupSpec(it) }
        )

    val spec = Spec(
        branches = branches,
        rootGroups = map.values.map { toGroupSpec(it) }
    )
    buildForest(root, idp, tpl, spec)
}
