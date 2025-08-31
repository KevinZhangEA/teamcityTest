package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.script

internal fun clientAndroidTemplateImpl(id: String, vcsRoot: VcsRoot) = Template {
    this.id(id)
    name = "tpl-client-android"

    params { param("GROUP_PATH",""); param("LEAF_KEY",""); param("BRANCH","")  }
    requirements { contains("teamcity.agent.jvm.os.name", "Windows") }

    vcs {
        root(vcsRoot)
        branchFilter = "+:%BRANCH%"
    }
    
    steps {
        script {
            name = "Produce artifact (Windows)"
            scriptContent = """
                echo helloworld (android)
                mkdir out
                setlocal enabledelayedexpansion
                set PROP_FILE=%TEAMCITY_BUILD_PROPERTIES_FILE%
                for /f "usebackq tokens=1,2 delims==" %%A in (`type !PROP_FILE!`) do (
                  if "%%A"=="GROUP_PATH" set GROUP_PATH=%%B
                  if "%%A"=="LEAF_KEY" set LEAF_KEY=%%B
                )
                (
                  echo groupPath: !GROUP_PATH!
                  echo leafKey:   !LEAF_KEY!
                  echo platform:  android
                  powershell -Command "Get-Date -Format yyyy-MM-ddTHH:mm:ssZ"
                ) > out/output.txt
            """.trimIndent()
        }
    }

    artifactRules = "out/**"
}
