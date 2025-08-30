package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

internal fun clientIosTemplateImpl(id: String) = Template {
    this.id(id)
    name = "tpl-client-ios"

    params { param("GROUP_PATH",""); param("LEAF_KEY","") }
    requirements { contains("teamcity.agent.jvm.os.name", "Linux") }
    
    vcs {
        root(DslContext.settingsRoot)
        branchFilter = "+:$br"
    }
    
    steps {
        script {
            name = "Produce artifact (Linux)"
            scriptContent = """
                set -e
                echo "helloworld (ios)"
                mkdir -p out
                PROP_FILE="${'$'}{TEAMCITY_BUILD_PROPERTIES_FILE:-}"
                val() { grep -E "^${'$'}1=" "${'$'}PROP_FILE" | sed -e "s/^${'$'}1=//" | head -n1 ; }
                {
                  echo "groupPath: $(val GROUP_PATH)"
                  echo "leafKey:   $(val LEAF_KEY)"
                  echo "platform:  ios"
                  date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ
                } > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}
