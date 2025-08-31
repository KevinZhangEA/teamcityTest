package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import lib.*

internal fun toolsTemplateImpl(id: String, vcsRoot: VcsRoot) = Template {
    this.id(id)
    name = "tpl-tools"

    params { param("GROUP_PATH",""); param("LEAF_KEY",""); param("BRANCH","") }
    // add shared VCS defaults with VCS root auto-detection
    addVcsParamsDefaults(vcsRoot)

    requirements { contains("teamcity.agent.jvm.os.name", "Windows") }

    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }
    
    steps {
        script {
            name = "Build tools (Windows)"
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                setlocal EnableDelayedExpansion
                
                if not exist codebase\buildscripts\build_tools.bat (
                  echo [error] missing codebase\buildscripts\build_tools.bat
                  exit /b 1
                )
                call codebase\buildscripts\build_tools.bat

                set rc=!errorlevel!
                echo [debug] build_tools.bat rc=!rc!
                if not "!rc!"=="0" (
                  echo [error] build_tools.bat failed with code !rc!
                  exit /b !rc!
                )

                if not exist out mkdir out
                (
                  echo groupPath: %GROUP_PATH%
                  echo leafKey:   %LEAF_KEY%
                  echo category:  tools
                  echo time:      %%date%% %%time%%
                ) > out/output.txt
            """.trimIndent()
        }
    }

    // append shared VCS submit step for Windows
    addVcsSubmitStepWindows()

    artifactRules = "out/**"
}
