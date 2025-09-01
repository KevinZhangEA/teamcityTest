package templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import lib.*
import lib.addP4SubmitStepUnix

internal fun defaultTemplateImpl(id: String, vcsRoot: VcsRoot, p4Stream: String) = Template {
    this.id(id)
    name = "tpl-default"

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
                set -e
                echo "helloworld"
                mkdir -p out
                {
                  echo "groupPath: ${'$'}{GROUP_PATH}"
                  echo "leafKey:   ${'$'}{LEAF_KEY}"
                  echo "buildId:   %teamcity.build.id%"
                  echo "buildNum:  %build.number%"
                  echo "branch:    %teamcity.build.branch%"
                  echo "agentName: %teamcity.agent.name%"
                  echo "agentOs:   %teamcity.agent.jvm.os.name%"
                  date -u +"%%Y-%%m-%%dT%%H:%%M:%%SZ"
                } > out/output.txt
            """.trimIndent()
        }
    }

    // append P4 submit step for Unix/Linux
    addP4SubmitStepUnix(VcsConfig.StepNames.LINUX, p4Stream)

    artifactRules = "out/**"
}
