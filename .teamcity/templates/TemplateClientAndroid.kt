package templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import lib.*

internal fun clientAndroidTemplateImpl(id: String, vcsRoot: VcsRoot, p4Stream: String) = Template {
    this.id(id)
    name = "tpl-client-android"

    params { param("GROUP_PATH","" ); param("LEAF_KEY","" ); param("BRANCH","" ) }

    requirements { contains("teamcity.agent.jvm.os.name", "Windows") }
    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }
    steps {
        script {
            name = "Build android (Windows)"
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                setlocal EnableDelayedExpansion
                
                if not exist codebase\buildscripts\build_android.bat (
                  echo [error] missing codebase\buildscripts\build_android.bat
                  exit /b 1
                )
                call codebase\buildscripts\build_android.bat

                set rc=!errorlevel!
                echo [debug] build_android.bat rc=!rc!
                if not "!rc!"=="0" (
                  echo [error] build_android.bat failed with code !rc!
                  exit /b !rc!
                )
                
                if not exist out mkdir out
                (
                  echo groupPath: %GROUP_PATH%
                  echo leafKey:   %LEAF_KEY%
                  echo platform:  android
                  echo time:      %%date%% %%time%%
                ) > out/output.txt
            """.trimIndent()
        }
    }
    // append P4 submit step for Windows
    addP4SubmitStepWindows(p4Stream)

    artifactRules = "out/**"
}
