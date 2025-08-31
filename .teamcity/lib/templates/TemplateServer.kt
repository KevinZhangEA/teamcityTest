package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

internal fun serverTemplateImpl(id: String, vcsRoot: VcsRoot) = Template {
    this.id(id)
    name = "tpl-server"

    params { param("GROUP_PATH",""); param("LEAF_KEY",""); param("BRANCH","")  }
    requirements { contains("teamcity.agent.jvm.os.name", "Linux") }
    
    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }

    steps {
        script {
            name = "Produce artifact (Linux)"
            scriptContent = """
                set -e
                echo "helloworld (server)"
                mkdir -p out
                PROP_FILE="${'$'}{TEAMCITY_BUILD_PROPERTIES_FILE:-}"
                val() { grep -E "^${'$'}1=" "${'$'}PROP_FILE" | sed -e "s/^${'$'}1=//" | head -n1 ; }
                {
                  echo "groupPath: $(val GROUP_PATH)"
                  echo "leafKey:   $(val LEAF_KEY)"
                  echo "role:      server"
                  date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ
                } > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}
