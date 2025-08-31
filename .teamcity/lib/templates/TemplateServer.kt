package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import lib.*

internal fun serverTemplateImpl(id: String, vcsRoot: VcsRoot, p4Stream: String) = Template {
    this.id(id)
    name = "tpl-server"

    params {
        param("GROUP_PATH","")
        param("LEAF_KEY","")
        param("BRANCH","")
    }

    requirements { contains("teamcity.agent.jvm.os.name", "Linux") }

    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }

    steps {
        script {
            name = "Produce artifact (Linux)"
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                set -euo pipefail
                bash codebase/buildscripts/build_servers.sh

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

    // append P4 submit step for Unix/Linux
    addP4SubmitStepUnix(VcsConfig.StepNames.LINUX, p4Stream)

    artifactRules = "out/**"
}
