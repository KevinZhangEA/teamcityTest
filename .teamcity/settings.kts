import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

version = "2024.03"

project {
    // ✅ 不要写 id("...")；这个 block 就是 Demo_code2config 自己
    // name = "Demo_code2config" // 可写可不写，写了要与 UI 完全一致

    // 一个演示子项目
    subProject(Project {
        id("Prj_branch1")          // 新的、未被占用的 externalId
        name = "branch1"

        // 一个演示构建
        buildType(BuildType {
            id("BT_branch1_demo")  // 新的 externalId
            name = "branch1_demo"
            steps {
                script {
                    name = "hello"
                    scriptContent = """echo "helloworld""""
                }
            }
        })
    })
}
