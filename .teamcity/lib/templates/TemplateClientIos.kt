package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

internal fun clientIosTemplateImpl(id: String, vcsRoot: VcsRoot) = Template {
    this.id(id)
    name = "tpl-client-ios"

    params { param("GROUP_PATH","" ); param("LEAF_KEY","" ); param("BRANCH","") }
    // add shared submit defaults
    addSubmitParamsDefaults()

    requirements { contains("teamcity.agent.jvm.os.name", "Mac") }

    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }

    steps {
        script {
            name = "Build ios (macOS)"
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                set -euo pipefail
                bash codebase/buildscripts/build_ios.sh
                
                # 生成 output.txt 以兼容旧流水线产物
                mkdir -p out
                {
                  echo "groupPath: %GROUP_PATH%"
                  echo "leafKey:   %LEAF_KEY%"
                  echo "platform:  ios"
                  date -u +"%%Y-%%m-%%dT%%H:%%M:%%SZ"
                } > out/output.txt
            """.trimIndent()
        }
    }

    // append shared submit step for Unix/macOS
    addSubmitStepUnix("Submit to VCS (macOS)")

    artifactRules = "out/**"
}
