package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

// Linux 基础模板（产出 out/output.txt）
private fun linuxBaseTemplate(id: String, label: String) = Template {
    this.id(id)
    name = label

    params {
        param("GROUP_PATH", "")
        param("LEAF_KEY", "")
    }
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

// 六套 Linux 模板
fun defaultTemplate(id: String)       = linuxBaseTemplate(id, "tpl-default")
fun clientIosTemplate(id: String)     = linuxBaseTemplate(id, "tpl-client-ios")
fun clientAndroidTemplate(id: String) = linuxBaseTemplate(id, "tpl-client-android")
fun serverTemplate(id: String)        = linuxBaseTemplate(id, "tpl-server")
fun toolsTemplate(id: String)         = linuxBaseTemplate(id, "tpl-tools")
fun assetsTemplate(id: String)        = linuxBaseTemplate(id, "tpl-assets")
