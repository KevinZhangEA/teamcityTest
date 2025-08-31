package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/**
 * Shared helpers to attach a "VCS Submit" step to templates.
 * 根据 VCS root 类型自动检测使用 Git 还是 Perforce
 * UI parameters to use:
 *  - Git: env.GIT_USER_EMAIL, env.GIT_USER_NAME, env.GIT_PUSH_URL (optional)
 *  - Perforce: env.P4USER, env.P4CLIENT, env.P4PORT, secure.P4PASSWD
 */

/**
 * 检测 VCS root 类型
 */
fun detectVcsType(vcsRoot: VcsRoot): String {
    return when (vcsRoot::class.java.name) {
        VcsConfig.VcsDetection.GIT_VCS_TYPE -> "git"
        VcsConfig.VcsDetection.PERFORCE_VCS_TYPE -> "perforce"
        else -> {
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
                set "_vcs=%${VcsConfig.EnvVars.VCS_TYPE}%"
                echo ${VcsConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "%_vcs%")}
                if /I "%_vcs%"=="git" (
                  where ${VcsConfig.Git.CHECK_COMMAND} >nul 2>&1 || ( echo ${VcsConfig.Messages.GIT_NOT_FOUND} & exit /b 0 )
                  if not defined GIT_USER_EMAIL set "GIT_USER_EMAIL=${VcsConfig.Git.DEFAULT_USER_EMAIL}"
                  if not defined GIT_USER_NAME  set "GIT_USER_NAME=${VcsConfig.Git.DEFAULT_USER_NAME}"
                  git config user.email "%GIT_USER_EMAIL%"
                  git config user.name  "%GIT_USER_NAME%"
                  if exist ${VcsConfig.Git.TRACKED_PATHS[0]} git add -A ${VcsConfig.Git.TRACKED_PATHS[0]}
                  if exist ${VcsConfig.Git.TRACKED_PATHS[1]} git add ${VcsConfig.Git.TRACKED_PATHS[1]}
                  git diff --cached --quiet && ( echo ${VcsConfig.Messages.NOTHING_TO_COMMIT} & exit /b 0 )
                  set "_msg=${VcsConfig.Git.COMMIT_MESSAGE_TEMPLATE}"
                  if defined GIT_PUSH_URL (
                    git commit -m "%_msg%" && git push "%GIT_PUSH_URL%" HEAD
                  ) else (
                    git commit -m "%_msg%" && git push origin HEAD
                  )
                ) else if /I "%_vcs%"=="perforce" (
                  where ${VcsConfig.Perforce.CHECK_COMMAND} >nul 2>&1 || ( echo ${VcsConfig.Messages.P4_NOT_FOUND} & exit /b 0 )
                  rem Export Perforce context from environment if provided (must be set in UI parameters)
                  if defined P4USER  set "P4USER=%P4USER%"
                  if defined P4CLIENT set "P4CLIENT=%P4CLIENT%"
                  if defined P4PORT   set "P4PORT=%P4PORT%"
                  rem Login using TeamCity secured password parameter; fallback to env P4PASSWD if present
                  p4 login -s >nul 2>&1
                  if errorlevel 1 (
                    (echo %secure.P4PASSWD%) | p4 login -a >nul 2>&1
                    if errorlevel 1 (
                      if defined P4PASSWD (
                        echo %P4PASSWD% | p4 login -a >nul 2>&1
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
                _vcs="%${VcsConfig.EnvVars.VCS_TYPE}%"
                echo "${VcsConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "${'$'}_vcs") }"
                case "${'$'}_vcs" in
                  git)
                    if ! command -v ${VcsConfig.Git.CHECK_COMMAND} >/dev/null 2>&1; then echo "${VcsConfig.Messages.GIT_NOT_FOUND}"; exit 0; fi
                    git config user.email "${'$'}{GIT_USER_EMAIL:-${VcsConfig.Git.DEFAULT_USER_EMAIL}}"
                    git config user.name  "${'$'}{GIT_USER_NAME:-${VcsConfig.Git.DEFAULT_USER_NAME}}"
                    [ -d ${VcsConfig.Git.TRACKED_PATHS[0]} ] && git add -A ${VcsConfig.Git.TRACKED_PATHS[0]} || true
                    [ -f ${VcsConfig.Git.TRACKED_PATHS[1]} ] && git add ${VcsConfig.Git.TRACKED_PATHS[1]} || true
                    if git diff --cached --quiet; then echo "${VcsConfig.Messages.NOTHING_TO_COMMIT}"; exit 0; fi
                    msg="${VcsConfig.Git.COMMIT_MESSAGE_TEMPLATE}"
                    if [ -n "${'$'}{GIT_PUSH_URL:-}" ]; then
                      git commit -m "${'$'}msg" && git push "${'$'}{GIT_PUSH_URL}" HEAD
                    else
                      git commit -m "${'$'}msg" && git push origin HEAD
                    fi
                    ;;
                  perforce)
                    if ! command -v ${VcsConfig.Perforce.CHECK_COMMAND} >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_NOT_FOUND}"; exit 0; fi
                    # Export Perforce context from environment if provided (must be set in UI parameters)
                    [ -n "${'$'}{P4USER:-}" ] && export P4USER || true
                    [ -n "${'$'}{P4CLIENT:-}" ] && export P4CLIENT || true
                    [ -n "${'$'}{P4PORT:-}" ] && export P4PORT   || true
                    # Login using TeamCity secured password parameter; fallback to env P4PASSWD if present
                    if ! p4 login -s >/dev/null 2>&1; then
                      printf '%s' "%secure.P4PASSWD%" | p4 login -a >/dev/null 2>&1 || true
                      if [ -n "${'$'}{P4PASSWD:-}" ]; then
                        printf '%s' "${'$'}{P4PASSWD}" | p4 login -a >/dev/null 2>&1 || true
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
