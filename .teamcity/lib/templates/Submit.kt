package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/**
 * Shared helpers to attach a gated "Submit to VCS" step to templates.
 * 根据 VCS root 类型自动检测使用 Git 还是 Perforce
 * Controlled by params:
 *  - SUBMIT: true/false (default false)
 * Optional env params for Git: GIT_USER_EMAIL, GIT_USER_NAME, GIT_PUSH_URL
 */

/**
 * 检测 VCS root 类型
 */
fun detectVcsType(vcsRoot: VcsRoot): String {
    return when (vcsRoot::class.java.name) {
        SubmitConfig.VcsDetection.GIT_VCS_TYPE -> "git"
        SubmitConfig.VcsDetection.PERFORCE_VCS_TYPE -> "perforce"
        else -> {
            // 尝试通过类名包含关键字来检测
            val className = vcsRoot::class.java.simpleName.lowercase()
            when {
                className.contains("git") -> "git"
                className.contains("perforce") || className.contains("p4") -> "perforce"
                else -> SubmitConfig.VcsDetection.DEFAULT_VCS_TYPE
            }
        }
    }
}

fun Template.addSubmitParamsDefaults(vcsRoot: VcsRoot) {
    val detectedVcsType = detectVcsType(vcsRoot)
    params {
        param(SubmitConfig.EnvVars.SUBMIT, SubmitConfig.Defaults.SUBMIT)
        param(SubmitConfig.EnvVars.VCS_TYPE, detectedVcsType)
    }
}

fun Template.addSubmitStepWindows() {
    steps {
        script {
            name = SubmitConfig.StepNames.WINDOWS
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                setlocal EnableExtensions EnableDelayedExpansion
                if /I not "%${SubmitConfig.EnvVars.SUBMIT}%"=="true" (
                  echo ${SubmitConfig.Messages.SUBMIT_DISABLED}
                  exit /b 0
                )
                set "_vcs=%${SubmitConfig.EnvVars.VCS_TYPE}%"
                echo ${SubmitConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "%_vcs%")}
                if /I "%_vcs%"=="git" (
                  where ${SubmitConfig.Git.CHECK_COMMAND} >nul 2>&1 || ( echo ${SubmitConfig.Messages.GIT_NOT_FOUND} & exit /b 0 )
                  if not defined ${SubmitConfig.EnvVars.GIT_USER_EMAIL} set "${SubmitConfig.EnvVars.GIT_USER_EMAIL}=${SubmitConfig.Git.DEFAULT_USER_EMAIL}"
                  if not defined ${SubmitConfig.EnvVars.GIT_USER_NAME}  set "${SubmitConfig.EnvVars.GIT_USER_NAME}=${SubmitConfig.Git.DEFAULT_USER_NAME}"
                  git config user.email "%${SubmitConfig.EnvVars.GIT_USER_EMAIL}%"
                  git config user.name  "%${SubmitConfig.EnvVars.GIT_USER_NAME}%"
                  if exist ${SubmitConfig.Git.TRACKED_PATHS[0]} git add -A ${SubmitConfig.Git.TRACKED_PATHS[0]}
                  if exist ${SubmitConfig.Git.TRACKED_PATHS[1]} git add ${SubmitConfig.Git.TRACKED_PATHS[1]}
                  git diff --cached --quiet && ( echo ${SubmitConfig.Messages.NOTHING_TO_COMMIT} & exit /b 0 )
                  set "_msg=${SubmitConfig.Git.COMMIT_MESSAGE_TEMPLATE}"
                  if defined ${SubmitConfig.EnvVars.GIT_PUSH_URL} (
                    git commit -m "%_msg%" && git push "%${SubmitConfig.EnvVars.GIT_PUSH_URL}%" HEAD
                  ) else (
                    git commit -m "%_msg%" && git push origin HEAD
                  )
                ) else if /I "%_vcs%"=="perforce" (
                  where ${SubmitConfig.Perforce.CHECK_COMMAND} >nul 2>&1 || ( echo ${SubmitConfig.Messages.P4_NOT_FOUND} & exit /b 0 )
                  p4 reconcile ${SubmitConfig.Perforce.RECONCILE_FLAGS} || ( echo ${SubmitConfig.Messages.P4_RECONCILE_FAILED} & exit /b 0 )
                  set "HASOPENED="
                  for /f "usebackq delims=" %%A in (`p4 opened ${SubmitConfig.Perforce.OPENED_CHECK_FLAGS} 2^>nul`) do set "HASOPENED=1"
                  if not defined HASOPENED (
                    echo ${SubmitConfig.Messages.NOTHING_TO_SUBMIT}
                    exit /b 0
                  )
                  p4 submit -d "${SubmitConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" || ( echo ${SubmitConfig.Messages.P4_SUBMIT_FAILED} & exit /b 0 )
                ) else (
                  echo [submit] unknown VCS type: %_vcs%, skip.
                  exit /b 0
                )
            """.trimIndent()
        }
    }
}

fun Template.addSubmitStepUnix(stepName: String) {
    steps {
        script {
            name = stepName
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                set -euo pipefail
                if [ "%${SubmitConfig.EnvVars.SUBMIT}%" != "true" ]; then echo "${SubmitConfig.Messages.SUBMIT_DISABLED}"; exit 0; fi
                _vcs="%${SubmitConfig.EnvVars.VCS_TYPE}%"
                echo "${SubmitConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "$_vcs")}"
                case "$_vcs" in
                  git)
                    if ! command -v ${SubmitConfig.Git.CHECK_COMMAND} >/dev/null 2>&1; then echo "${SubmitConfig.Messages.GIT_NOT_FOUND}"; exit 0; fi
                    git config user.email "${'$'}{${SubmitConfig.EnvVars.GIT_USER_EMAIL}:-${SubmitConfig.Git.DEFAULT_USER_EMAIL}}"
                    git config user.name  "${'$'}{${SubmitConfig.EnvVars.GIT_USER_NAME}:-${SubmitConfig.Git.DEFAULT_USER_NAME}}"
                    [ -d ${SubmitConfig.Git.TRACKED_PATHS[0]} ] && git add -A ${SubmitConfig.Git.TRACKED_PATHS[0]} || true
                    [ -f ${SubmitConfig.Git.TRACKED_PATHS[1]} ] && git add ${SubmitConfig.Git.TRACKED_PATHS[1]} || true
                    if git diff --cached --quiet; then echo "${SubmitConfig.Messages.NOTHING_TO_COMMIT}"; exit 0; fi
                    msg="${SubmitConfig.Git.COMMIT_MESSAGE_TEMPLATE}"
                    if [ -n "%${SubmitConfig.EnvVars.GIT_PUSH_URL}%" ]; then
                      git commit -m "${'$'}msg" && git push "%${SubmitConfig.EnvVars.GIT_PUSH_URL}%" HEAD
                    else
                      git commit -m "${'$'}msg" && git push origin HEAD
                    fi
                    ;;
                  perforce)
                    if ! command -v ${SubmitConfig.Perforce.CHECK_COMMAND} >/dev/null 2>&1; then echo "${SubmitConfig.Messages.P4_NOT_FOUND}"; exit 0; fi
                    if ! p4 reconcile ${SubmitConfig.Perforce.RECONCILE_FLAGS}; then echo "${SubmitConfig.Messages.P4_RECONCILE_FAILED}"; exit 0; fi
                    if [ "$(p4 opened ${SubmitConfig.Perforce.OPENED_CHECK_FLAGS} 2>/dev/null | wc -l | tr -d ' ')" = "0" ]; then echo "${SubmitConfig.Messages.NOTHING_TO_SUBMIT}"; exit 0; fi
                    p4 submit -d "${SubmitConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" || { echo "${SubmitConfig.Messages.P4_SUBMIT_FAILED}"; exit 0; }
                    ;;
                  *)
                    echo "[submit] unknown VCS type: $_vcs, skip."; exit 0;;
                esac
            """.trimIndent()
        }
    }
}
