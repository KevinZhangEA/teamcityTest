package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

internal fun defaultTemplateImpl(id: String, vcsRoot: VcsRoot) = Template {
    this.id(id)
    name = "tpl-default"

    params { param("GROUP_PATH",""); param("LEAF_KEY",""); param("BRANCH","") }
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
                echo "helloworld"
                mkdir -p out
                {
                  echo "groupPath: ${'$'}{GROUP_PATH}"
                  echo "leafKey:   ${'$'}{LEAF_KEY}"
                  echo "buildConf: ${'$'}{teamcity_buildConfName}"
                  echo "buildId:   ${'$'}{teamcity_build_id}"
                  echo "buildNum:  ${'$'}{build_number}"
                  echo "branch:    ${'$'}{teamcity_build_branch}"
                  echo "agentName: ${'$'}{teamcity_agent_name}"
                  echo "agentOs:   ${'$'}{teamcity_agent.jvm.os.name}"
                  date -u +%%Y-%%m-%%dT%%H:%%M:%%SZ
                } > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}
