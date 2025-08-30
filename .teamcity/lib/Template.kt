package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/** 通用演示模板（Linux-only）：生成 out/output.txt 作为工件 */
fun demoTemplate(id: String) = Template {
    this.id(id)
    name = "tpl-demo"

    // 通用占位参数（避免“必填”叹号）
    params {
        param("GROUP_PATH", "")
        param("LEAF_KEY", "")
        // 你也可以在 settings.kts 的每个 leaf 里附加更多自定义参数，这里无需声明
    }

    // 仅在 Linux 代理上运行
    requirements { contains("teamcity.agent.jvm.os.name", "Linux") }

    steps {
        script {
            name = "Hello + Produce artifact (Linux)"
            scriptContent = """
                set -e
                echo "helloworld"
                mkdir -p out

                PROP_FILE="${'$'}{TEAMCITY_BUILD_PROPERTIES_FILE:-}"
                val() { grep -E "^${'$'}1=" "${'$'}PROP_FILE" | sed -e "s/^${'$'}1=//" | head -n1 ; }

                groupPath="$(val GROUP_PATH)"
                leafKey="$(val LEAF_KEY)"

                {
                  echo "groupPath: ${'$'}groupPath"
                  echo "leafKey: ${'$'}leafKey"
                  echo "buildConf: $(val teamcity.buildConfName)"
                  echo "buildId: $(val teamcity.build.id)"
                  echo "buildNumber: $(val build.number)"
                  echo "branch: $(val teamcity.build.branch)"
                  echo "agentName: $(val teamcity.agent.name)"
                  echo "agentOs: $(val teamcity.agent.jvm.os.name)"
                  date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ
                } > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}

