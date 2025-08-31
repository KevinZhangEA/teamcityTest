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
 * Optional env params for Perforce: P4USER, P4CLIENT, P4PORT, P4PASSWD
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
                  rem 设置 Perforce 环境变量（如果没有定义）
                  if not defined ${VcsConfig.EnvVars.P4_USER} set "${VcsConfig.EnvVars.P4_USER}=${VcsConfig.Perforce.DEFAULT_USER}"
                  if not defined ${VcsConfig.EnvVars.P4_CLIENT} set "${VcsConfig.EnvVars.P4_CLIENT}=${VcsConfig.Perforce.DEFAULT_CLIENT}"
                  rem 显示 P4 连接信息
                  echo [submit] P4 User: %${VcsConfig.EnvVars.P4_USER}%
                  echo [submit] P4 Client: %${VcsConfig.EnvVars.P4_CLIENT}%
                  echo [submit] P4 Port: %${VcsConfig.EnvVars.P4_PORT}%
                  rem 验证 P4 连接
                  p4 info >nul 2>&1 || ( echo [submit] p4 connection failed, check P4PORT/P4USER/P4CLIENT/P4PASSWD & exit /b 0 )
                  p4 reconcile ${VcsConfig.Perforce.RECONCILE_FLAGS} || ( echo ${VcsConfig.Messages.P4_RECONCILE_FAILED} & exit /b 0 )
                  set "HASOPENED="
                  for /f "usebackq delims=" %%A in (`p4 opened ${VcsConfig.Perforce.OPENED_CHECK_FLAGS} 2^>nul`) do set "HASOPENED=1"
                  if not defined HASOPENED (
                    echo ${VcsConfig.Messages.NOTHING_TO_SUBMIT}
                    exit /b 0
                  )
                  p4 submit -d "${VcsConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" || ( echo ${VcsConfig.Messages.P4_SUBMIT_FAILED} & exit /b 0 )
                ) else (
                  echo [submit] unknown VCS type: %_vcs%, skip.
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
                echo "${VcsConfig.Messages.VCS_AUTO_DETECTED.replace("%s", "$_vcs")}"
                case "$_vcs" in
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
                    # 设置 Perforce 环境变量（如果没有定义）
                    export ${VcsConfig.EnvVars.P4_USER}="${VcsConfig.EnvVars.P4_USER}:-${VcsConfig.Perforce.DEFAULT_USER}"
                    export ${VcsConfig.EnvVars.P4_CLIENT}="${VcsConfig.EnvVars.P4_CLIENT}:-${VcsConfig.Perforce.DEFAULT_CLIENT}"
                    # 显示 P4 连接信息
                    echo "[submit] P4 User: ${'$'}${VcsConfig.EnvVars.P4_USER}"
                    echo "[submit] P4 Client: ${'$'}${VcsConfig.EnvVars.P4_CLIENT}"
                    echo "[submit] P4 Port: ${'$'}${VcsConfig.EnvVars.P4_PORT}"
                    # 验证 P4 连接
                    if ! p4 info >/dev/null 2>&1; then echo "[submit] p4 connection failed, check P4PORT/P4USER/P4CLIENT/P4PASSWD"; exit 0; fi
                    if ! p4 reconcile ${VcsConfig.Perforce.RECONCILE_FLAGS}; then echo "${VcsConfig.Messages.P4_RECONCILE_FAILED}"; exit 0; fi
                    if [ "$(p4 opened ${VcsConfig.Perforce.OPENED_CHECK_FLAGS} 2>/dev/null | wc -l | tr -d ' ')" = "0" ]; then echo "${VcsConfig.Messages.NOTHING_TO_SUBMIT}"; exit 0; fi
                    p4 submit -d "${VcsConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" || { echo "${VcsConfig.Messages.P4_SUBMIT_FAILED}"; exit 0; }
                    ;;
                  *)
                    echo "[submit] unknown VCS type: $_vcs, skip."; exit 0;;
                esac
            """.trimIndent()
        }
    }
}
