package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings

/** 一片“森林”的声明：多个分支，每个分支挂一棵分组树 */
data class Spec(
    val branches: List<String>,
    val rootGroups: List<GroupSpec>
)

/** 分组声明：可以有叶子（build）和子分组；每个分组自动生成一个 finalize */
data class GroupSpec(
    val id: String,                 // 机器可读（用于 externalId/路径）
    val name: String,               // 人类可读（UI 展示）
    val leaves: List<LeafSpec> = emptyList(),
    val groups: List<GroupSpec> = emptyList()
)

/** 叶子声明：一个 build；params 可附加任意键值做业务区分 */
data class LeafSpec(
    val key: String,                // 机器可读（用于 externalId）
    val displayName: String? = null,// 可选 UI 名；默认用 "<branch>_<groupPath>_<key>"
    val params: Map<String,String> = emptyMap()
)

private data class BuiltGroup(
    val allLeaves: List<BuildType>,
    val finalize: BuildType
)

fun buildForest(root: Project, idp: String, tpl: Template, spec: Spec) {
    spec.branches.forEach { br ->
        // 分支根
        val prjBranch = Project { id("${idp}_Prj_$br"); name = br }
        root.subProject(prjBranch)

        // 入口与分发（不区分类别）
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

        // 递归构建分组树
        val builtGroups = spec.rootGroups.map { g ->
            buildGroup(prjBranch, idp, br, g, tpl, dispatcher, parentPath = g.id)
        }

        // Composite 只做链路看板：挂上 dispatcher + 所有叶子 + 各层 finalize
        composite.dependencies {
            snapshot(dispatcher) { synchronizeRevisions = true }
            builtGroups.forEach { bg ->
                bg.allLeaves.forEach { snapshot(it) { synchronizeRevisions = true } }
                snapshot(bg.finalize) { synchronizeRevisions = true }
            }
        }
    }
}

private fun buildGroup(
    parent: Project,
    idp: String,
    br: String,
    g: GroupSpec,
    tpl: Template,
    dispatcher: BuildType,
    parentPath: String
): BuiltGroup {
    // 当前分组项目
    val prj = Project { id("${idp}_Prj_${br}_${parentPath}"); name = g.name }
    parent.subProject(prj)

    // 生成叶子构建
    val leaves = g.leaves.map { leaf ->
        BuildType().also { bt ->
            bt.id("${idp}_BT_${br}_${parentPath}_${leaf.key}")
            val defaultName = "${br}_${parentPath}_${leaf.key}"
            bt.name = leaf.displayName ?: defaultName
            bt.templates(tpl)
            bt.params {
                // 通用位置信息
                param("GROUP_PATH", parentPath)
                param("LEAF_KEY", leaf.key)
                // 附加业务参数
                leaf.params.forEach { (k, v) -> param(k, v) }
            }
            bt.dependencies { snapshot(dispatcher) { synchronizeRevisions = true } }
            prj.buildType(bt)
        }
    }

    // 递归子分组
    val childGroups = g.groups.map { sub ->
        val subPath = "${parentPath}_${sub.id}"
        buildGroup(prj, idp, br, sub, tpl, dispatcher, parentPath = subPath)
    }

    // 当前分组 finalize：等待【本组叶子 + 每个子分组的 finalize】完成，并收集它们的工件
    val fin = BuildType {
        id("${idp}_BT_${br}_${parentPath}_finalize")
        name = "99_🏁 ${g.name}_finalize"
        templates(tpl)
        dependencies {
            leaves.forEach { child ->
                snapshot(child) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                artifacts(child) { artifactRules = "** => inputs/${child.name}/" }
            }
            childGroups.forEach { cg ->
                snapshot(cg.finalize) { synchronizeRevisions = true; onDependencyFailure = FailureAction.ADD_PROBLEM }
                artifacts(cg.finalize) { artifactRules = "** => inputs/${cg.finalize.name}/" }
            }
        }
    }
    prj.buildType(fin)

    // 汇总当前分支下“所有叶子”（含子分组的）
    val allLeaves = leaves + childGroups.flatMap { it.allLeaves }
    return BuiltGroup(allLeaves = allLeaves, finalize = fin)
}

