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
                {
                  echo "groupPath: %GROUP_PATH%"
                  echo "leafKey:   %LEAF_KEY%"
                  echo "role:      server"
                  date -u +"%%Y-%%m-%%dT%%H:%%M:%%SZ"
                } > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}
