package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/** 通用演示模板（Linux-only）：生成 out/output.txt 作为工件 */
fun demoTemplate(id: String) = Template {
    this.id(id)
    name = "tpl-demo"

    // 通用占位参数，给默认值避免“必填”叹号
    params {
        param("GROUP_PATH", "")
        param("LEAF_KEY", "")
    }

    // 仅 Linux 代理
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

                {
                  echo "groupPath: $(val GROUP_PATH)"
                  echo "leafKey:   $(val LEAF_KEY)"
                  echo "buildConf: $(val teamcity.buildConfName)"
                  echo "buildId:   $(val teamcity.build.id)"
                  echo "buildNum:  $(val build.number)"
                  echo "branch:    $(val teamcity.build.branch)"
                  echo "agentName: $(val teamcity.agent.name)"
                  echo "agentOs:   $(val teamcity.agent.jvm.os.name)"
                  date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ
                } > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}
