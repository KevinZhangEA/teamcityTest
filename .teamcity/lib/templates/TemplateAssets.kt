package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

internal fun assetsTemplateImpl(id: String, vcsRoot: VcsRoot) = Template {
    this.id(id)
    name = "tpl-assets"

    params { param("GROUP_PATH","" ); param("LEAF_KEY","" ); param("BRANCH","") }
    requirements { contains("teamcity.agent.jvm.os.name", "Windows") }
    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }
    steps {
        script {
            name = "Build assets (Windows)"
            scriptContent = """
                call codebase\buildscripts\build_tools.bat

                set "rc=%%errorlevel%%"
                echo [debug] build_tools.bat rc=%rc%
                if not "%rc%"=="0" (
                  echo [error] build_tools.bat failed with code %rc%
                  exit /b %rc%
                )
                
                if not exist out mkdir out
                (
                  echo groupPath: %GROUP_PATH%
                  echo leafKey:   %LEAF_KEY%
                  echo category:  assets
                  echo time:      %%date%% %%time%%
                ) > out/output.txt
            """.trimIndent()
        }
    }
    artifactRules = "out/**"
}
