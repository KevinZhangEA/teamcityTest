package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/**
 * Shared helpers to attach a gated "VCS Submit" step to templates.
 * 根据 VCS root 类型自动检测使用 Git 还是 Perforce
 * Controlled by params:
 *  - VCS_SUBMIT: true/false (default false)
 * Optional env params for Git: GIT_USER_EMAIL, GIT_USER_NAME, GIT_PUSH_URL
 * Optional env params for Perforce: P4USER, P4CLIENT, P4PORT, P4PASSWD, P4TICKET
 */

/**
 * 检测 VCS root 类型
 */
fun detectVcsType(vcsRoot: VcsRoot): String {
    return when (vcsRoot::class.java.name) {
        VcsConfig.VcsDetection.GIT_VCS_TYPE -> "git"
        VcsConfig.VcsDetection.PERFORCE_VCS_TYPE -> "perforce"
        else -> {
            // 尝试通过类名包含关键字来检测
            val className = vcsRoot::class.java.simpleName.lowercase()
            when {
                className.contains("git") -> "git"
                className.contains("perforce") || className.contains("p4") -> "perforce"
                else -> VcsConfig.VcsDetection.DEFAULT_VCS_TYPE
            }
        }
    }
}

fun Template.addVcsParamsDefaults(vcsRoot: VcsRoot) {
    val detectedVcsType = detectVcsType(vcsRoot)
    params {
        param(VcsConfig.EnvVars.VCS_SUBMIT, VcsConfig.Defaults.VCS_SUBMIT)
        param(VcsConfig.EnvVars.VCS_TYPE, detectedVcsType)
    }
}

fun Template.addVcsSubmitStepWindows() {
    steps {
        script {
            name = VcsConfig.StepNames.WINDOWS
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                setlocal EnableExtensions EnableDelayedExpansion
                if /I not "%${VcsConfig.EnvVars.VCS_SUBMIT}%"=="true" (
                  echo ${VcsConfig.Messages.VCS_SUBMIT_DISABLED}
                  exit /b 0
                )
                set "_vcs=%${VcsConfig.EnvVars.VCS_TYPE}%"
                echo ${VcsConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "%_vcs%")}
                if /I "%_vcs%"=="git" (
                  where ${VcsConfig.Git.CHECK_COMMAND} >nul 2>&1 || ( echo ${VcsConfig.Messages.GIT_NOT_FOUND} & exit /b 0 )
                  if not defined ${VcsConfig.EnvVars.GIT_USER_EMAIL} set "${VcsConfig.EnvVars.GIT_USER_EMAIL}=${VcsConfig.Git.DEFAULT_USER_EMAIL}"
                  if not defined ${VcsConfig.EnvVars.GIT_USER_NAME}  set "${VcsConfig.EnvVars.GIT_USER_NAME}=${VcsConfig.Git.DEFAULT_USER_NAME}"
                  git config user.email "%${VcsConfig.EnvVars.GIT_USER_EMAIL}%"
                  git config user.name  "%${VcsConfig.EnvVars.GIT_USER_NAME}%"
                  if exist ${VcsConfig.Git.TRACKED_PATHS[0]} git add -A ${VcsConfig.Git.TRACKED_PATHS[0]}
                  if exist ${VcsConfig.Git.TRACKED_PATHS[1]} git add ${VcsConfig.Git.TRACKED_PATHS[1]}
                  git diff --cached --quiet && ( echo ${VcsConfig.Messages.NOTHING_TO_COMMIT} & exit /b 0 )
                  set "_msg=${VcsConfig.Git.COMMIT_MESSAGE_TEMPLATE}"
                  if defined ${VcsConfig.EnvVars.GIT_PUSH_URL} (
                    git commit -m "%_msg%" && git push "%${VcsConfig.EnvVars.GIT_PUSH_URL}%" HEAD
                  ) else (
                    git commit -m "%_msg%" && git push origin HEAD
                  )
                ) else if /I "%_vcs%"=="perforce" (
                  where ${VcsConfig.Perforce.CHECK_COMMAND} >nul 2>&1 || ( echo ${VcsConfig.Messages.P4_NOT_FOUND} & exit /b 0 )
                  if not defined ${VcsConfig.EnvVars.P4_USER} set "${VcsConfig.EnvVars.P4_USER}=${VcsConfig.Perforce.DEFAULT_USER}"
                  if not defined ${VcsConfig.EnvVars.P4_CLIENT} set "${VcsConfig.EnvVars.P4_CLIENT}=${VcsConfig.Perforce.DEFAULT_CLIENT}"
                  if not defined ${VcsConfig.EnvVars.P4_PORT} set "${VcsConfig.EnvVars.P4_PORT}=${VcsConfig.Perforce.DEFAULT_PORT}"
                  echo [vcs] P4 User: %${VcsConfig.EnvVars.P4_USER}%
                  echo [vcs] P4 Client: %${VcsConfig.EnvVars.P4_CLIENT}%
                  echo [vcs] P4 Port: %${VcsConfig.EnvVars.P4_PORT}%
                  rem Login if needed using TeamCity password parameter first; fallback to env var
                  p4 login -s >nul 2>&1
                  if errorlevel 1 (
                    rem Try secure TeamCity parameter (masked in logs)
                    (echo %secure.P4PASSWD%) | p4 login -a >nul 2>&1
                    if errorlevel 1 (
                      if defined ${VcsConfig.EnvVars.P4_PASSWD} (
                        echo %${VcsConfig.EnvVars.P4_PASSWD}% | p4 login -a >nul 2>&1
                      )
                    )
                  )
                  p4 info >nul 2>&1 || ( echo [vcs] p4 connection failed, please check authentication & exit /b 0 )
                  p4 reconcile ${VcsConfig.Perforce.RECONCILE_FLAGS} >nul 2>&1 || ( echo ${VcsConfig.Messages.P4_RECONCILE_FAILED} & exit /b 0 )
                  set "HASOPENED="
                  for /f "usebackq delims=" %%A in (`p4 opened ${VcsConfig.Perforce.OPENED_CHECK_FLAGS} 2^>nul`) do set "HASOPENED=1"
                  if not defined HASOPENED (
                    echo ${VcsConfig.Messages.NOTHING_TO_SUBMIT}
                    exit /b 0
                  )
                  p4 submit -d "${VcsConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" >nul 2>&1 || ( echo ${VcsConfig.Messages.P4_SUBMIT_FAILED} & exit /b 0 )
                  echo [vcs] P4 submit completed successfully
                ) else (
                  echo [vcs] unknown VCS type: %_vcs%, skip.
                  exit /b 0
                )
            """.trimIndent()
        }
    }
}

fun Template.addVcsSubmitStepUnix(stepName: String) {
    steps {
        script {
            name = stepName
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                set -euo pipefail
                if [ "%${VcsConfig.EnvVars.VCS_SUBMIT}%" != "true" ]; then echo "${VcsConfig.Messages.VCS_SUBMIT_DISABLED}"; exit 0; fi
                _vcs="%${VcsConfig.EnvVars.VCS_TYPE}%"
                echo "${VcsConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "${'$'}_vcs") }"
                case "${'$'}_vcs" in
                  git)
                    if ! command -v ${VcsConfig.Git.CHECK_COMMAND} >/dev/null 2>&1; then echo "${VcsConfig.Messages.GIT_NOT_FOUND}"; exit 0; fi
                    git config user.email "${'$'}{${VcsConfig.EnvVars.GIT_USER_EMAIL}:-${VcsConfig.Git.DEFAULT_USER_EMAIL}}"
                    git config user.name  "${'$'}{${VcsConfig.EnvVars.GIT_USER_NAME}:-${VcsConfig.Git.DEFAULT_USER_NAME}}"
                    [ -d ${VcsConfig.Git.TRACKED_PATHS[0]} ] && git add -A ${VcsConfig.Git.TRACKED_PATHS[0]} || true
                    [ -f ${VcsConfig.Git.TRACKED_PATHS[1]} ] && git add ${VcsConfig.Git.TRACKED_PATHS[1]} || true
                    if git diff --cached --quiet; then echo "${VcsConfig.Messages.NOTHING_TO_COMMIT}"; exit 0; fi
                    msg="${VcsConfig.Git.COMMIT_MESSAGE_TEMPLATE}"
                    if [ -n "%${VcsConfig.EnvVars.GIT_PUSH_URL}%" ]; then
                      git commit -m "${'$'}msg" && git push "%${VcsConfig.EnvVars.GIT_PUSH_URL}%" HEAD
                    else
                      git commit -m "${'$'}msg" && git push origin HEAD
                    fi
                    ;;
                  perforce)
                    if ! command -v ${VcsConfig.Perforce.CHECK_COMMAND} >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_NOT_FOUND}"; exit 0; fi
                    export ${VcsConfig.EnvVars.P4_USER}="${VcsConfig.EnvVars.P4_USER}:-${VcsConfig.Perforce.DEFAULT_USER}"
                    export ${VcsConfig.EnvVars.P4_CLIENT}="${VcsConfig.EnvVars.P4_CLIENT}:-${VcsConfig.Perforce.DEFAULT_CLIENT}"
                    export ${VcsConfig.EnvVars.P4_PORT}="${VcsConfig.EnvVars.P4_PORT}:-${VcsConfig.Perforce.DEFAULT_PORT}"
                    if ! p4 login -s >/dev/null 2>&1; then
                      printf '%s' "%secure.P4PASSWD%" | p4 login -a >/dev/null 2>&1 || true
                      if [ -n "${'$'}{${VcsConfig.EnvVars.P4_PASSWD}:-}" ]; then
                        printf '%s' "${'$'}{${VcsConfig.EnvVars.P4_PASSWD}}" | p4 login -a >/dev/null 2>&1 || true
                      fi
                    fi
                    if ! p4 info >/dev/null 2>&1; then echo "[vcs] p4 connection failed, please check authentication"; exit 0; fi
                    if ! p4 reconcile ${VcsConfig.Perforce.RECONCILE_FLAGS} >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_RECONCILE_FAILED}"; exit 0; fi
                    if [ "$(p4 opened ${VcsConfig.Perforce.OPENED_CHECK_FLAGS} 2>/dev/null | wc -l | tr -d ' ')" = "0" ]; then echo "${VcsConfig.Messages.NOTHING_TO_SUBMIT}"; exit 0; fi
                    if ! p4 submit -d "${VcsConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_SUBMIT_FAILED}"; exit 0; fi
                    echo "[vcs] P4 submit completed successfully"
                    ;;
                  *)
                    echo "[vcs] unknown VCS type: ${'$'}_vcs, skip."; exit 0;;
                esac
            """.trimIndent()
        }
    }
}
