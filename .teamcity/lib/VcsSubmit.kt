package lib

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/**
 * Shared helpers to attach a "VCS Submit" step to templates.
 * 根据 VCS root 类型自动检测使用 Git 还是 Perforce
 * UI parameters to use:
 *  - Git: env.GIT_USER_EMAIL, env.GIT_USER_NAME, env.GIT_PUSH_URL (optional)
 *  - Perforce: env.P4USER, env.P4PORT, secure.P4PASSWD, env.P4_STREAM (optional for auto-create)
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

fun Template.addVcsSubmitStepWindows(p4Stream: String? = null) {
    steps {
        script {
            name = VcsConfig.StepNames.WINDOWS
            workingDir = "%teamcity.build.checkoutDir%"
            val streamLiteral = p4Stream?.trim() ?: ""
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
                  rem Derive P4CLIENT per agent if not provided
                  if not defined P4CLIENT (
                    set "_agent=%teamcity.agent.name%"
                    set "P4CLIENT=tc-%_agent%"
                    set "P4CLIENT=%P4CLIENT: =_%"
                  )
                  if defined P4USER  set "P4USER=%P4USER%"
                  if defined P4PORT   set "P4PORT=%P4PORT%"
                  set "_stream=${streamLiteral}"
                  if not defined _stream if defined P4_STREAM set "_stream=%P4_STREAM%"
                  echo [vcs] P4 User: %P4USER%
                  echo [vcs] P4 Client: %P4CLIENT%
                  echo [vcs] P4 Port: %P4PORT%
                  if defined _stream echo [vcs] P4 Stream: !_stream!
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
                  rem Ensure P4 client exists; if missing and stream provided, auto-create a stream client
                  p4 client -o "%P4CLIENT%" >nul 2>&1
                  if errorlevel 1 (
                    if defined _stream (
                      for /f %%H in ('hostname') do set "_host=%%H"
                      set "_root=%teamcity.build.checkoutDir%"
                      (
                        echo Client: %P4CLIENT%
                        if defined P4USER echo Owner: %P4USER%
                        echo Host: %_host%
                        echo Root: %_root%
                        echo Options: noallwrite clobber nocompress unlocked nomodtime rmdir
                        echo LineEnd: local
                        echo Stream: %_stream%
                        echo Type: stream
                      ) > __p4client.txt
                      p4 client -f -i < __p4client.txt >nul 2>&1
                      del __p4client.txt
                      p4 client -o "%P4CLIENT%" >nul 2>&1 || ( echo [vcs] failed to create stream client "%P4CLIENT%"; skip. & exit /b 0 )
                    ) else (
                      echo [vcs] p4 client "%P4CLIENT%" not found; set env.P4_STREAM to auto-create, or pre-create the client; skip.
                      exit /b 0
                    )
                  )
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

fun Template.addVcsSubmitStepUnix(stepName: String, p4Stream: String? = null) {
    steps {
        script {
            name = stepName
            workingDir = "%teamcity.build.checkoutDir%"
            val streamLiteral = p4Stream?.trim() ?: ""
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
                    # Derive P4CLIENT per agent if not provided; sanitize to allowed charset
                    if [ -z "${'$'}{P4CLIENT:-}" ]; then
                      _agent="%teamcity.agent.name%"
                      P4CLIENT="tc-$(printf '%s' "${'$'}_agent" | tr -c 'A-Za-z0-9_-' '_')"
                      export P4CLIENT
                    fi
                    [ -n "${'$'}{P4USER:-}" ] && export P4USER || true
                    [ -n "${'$'}{P4PORT:-}" ] && export P4PORT   || true
                    STREAM="${streamLiteral}"
                    if [ -z "${'$'}STREAM" ] && [ -n "${'$'}{P4_STREAM:-}" ]; then STREAM="${'$'}{P4_STREAM}"; fi
                    [ -n "${'$'}{P4USER:-}" ] && echo "[vcs] P4 User: ${'$'}{P4USER}" || true
                    echo "[vcs] P4 Client: ${'$'}{P4CLIENT}"
                    [ -n "${'$'}{P4PORT:-}" ] && echo "[vcs] P4 Port: ${'$'}{P4PORT}" || true
                    [ -n "${'$'}STREAM" ] && echo "[vcs] P4 Stream: ${'$'}STREAM" || true
                    if ! p4 login -s >/dev/null 2>&1; then
                      printf '%s' "%secure.P4PASSWD%" | p4 login -a >/dev/null 2>&1 || true
                      if [ -n "${'$'}{P4PASSWD:-}" ]; then
                        printf '%s' "${'$'}{P4PASSWD}" | p4 login -a >/dev/null 2>&1 || true
                      fi
                    fi
                    if ! p4 info >/dev/null 2>&1; then echo "[vcs] p4 connection failed, please check authentication"; exit 0; fi
                    if ! p4 client -o "${'$'}{P4CLIENT}" >/dev/null 2>&1; then
                      if [ -n "${'$'}STREAM" ]; then
                        host="$(hostname)"
                        cat > __p4client.txt <<EOF
Client: ${'$'}{P4CLIENT}
${'$'}([ -n "${'$'}{P4USER:-}" ] && echo "Owner: ${'$'}{P4USER}" )
Host: ${'$'}{host}
Root: %teamcity.build.checkoutDir%
Options: noallwrite clobber nocompress unlocked nomodtime rmdir
LineEnd: local
Stream: ${'$'}{STREAM}
Type: stream
EOF
                        sed -i.bak '/^$/d' __p4client.txt 2>/dev/null || true
                        rm -f __p4client.txt.bak 2>/dev/null || true
                        if ! p4 client -f -i < __p4client.txt >/dev/null 2>&1; then echo "[vcs] failed to create stream client '${'$'}{P4CLIENT}'"; rm -f __p4client.txt; exit 0; fi
                        rm -f __p4client.txt
                        p4 client -o "${'$'}{P4CLIENT}" >/dev/null 2>&1 || { echo "[vcs] stream client verification failed for '${'$'}{P4CLIENT}'"; exit 0; }
                      else
                        echo "[vcs] p4 client '${'$'}{P4CLIENT}' not found; set env.P4_STREAM to auto-create, or pre-create the client; skip."; exit 0
                      fi
                    fi
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
