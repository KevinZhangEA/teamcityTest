package lib

import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.script

fun Template.addP4SubmitStepWindows(p4Stream: String) {
    steps {
        script {
            name = VcsConfig.StepNames.WINDOWS
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                setlocal EnableDelayedExpansion
                if "${p4Stream}"=="" (
                  echo [vcs] no P4 stream provided, skip.
                  exit /b 0
                )
                where ${VcsConfig.Perforce.CHECK_COMMAND} >nul 2>&1 || ( echo ${VcsConfig.Messages.P4_NOT_FOUND} & exit /b 0 )
                rem Derive P4CLIENT per agent if not provided
                if not defined P4CLIENT (
                  set "_agent=%teamcity.agent.name%"
                  set "P4CLIENT=tc-%_agent%"
                  set "P4CLIENT=%P4CLIENT: =_%"
                )
                if defined P4USER  set "P4USER=%P4USER%"
                if defined P4PORT   set "P4PORT=%P4PORT%"
                echo [vcs] P4 User: %P4USER%
                echo [vcs] P4 Client: %P4CLIENT%
                echo [vcs] P4 Port: %P4PORT%
                echo [vcs] P4 Stream: ${p4Stream}
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
                rem Ensure P4 client exists; if missing, auto-create a stream client
                p4 client -o "%P4CLIENT%" >nul 2>&1
                if errorlevel 1 (
                  for /f %%H in ('hostname') do set "_host=%%H"
                  set "_root=%teamcity.build.checkoutDir%"
                  (
                    echo Client: %P4CLIENT%
                    if defined P4USER echo Owner: %P4USER%
                    echo Host: %_host%
                    echo Root: %_root%
                    echo Options: noallwrite clobber nocompress unlocked nomodtime rmdir
                    echo LineEnd: local
                    echo Stream: ${p4Stream}
                    echo Type: stream
                  ) > __p4client.txt
                  p4 client -f -i < __p4client.txt >nul 2>&1
                  del __p4client.txt
                  p4 client -o "%P4CLIENT%" >nul 2>&1 || ( echo [vcs] failed to create stream client "%P4CLIENT%"; skip. & exit /b 0 )
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
            """.trimIndent()
        }
    }
}

fun Template.addP4SubmitStepUnix(stepName: String, p4Stream: String) {
    steps {
        script {
            name = stepName
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                set -euo pipefail
                if [ "${p4Stream}" = "" ]; then echo "[vcs] no P4 stream provided, skip."; exit 0; fi
                if ! command -v ${VcsConfig.Perforce.CHECK_COMMAND} >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_NOT_FOUND}"; exit 0; fi
                # Derive P4CLIENT per agent if not provided; sanitize to allowed charset
                if [ -z "${'$'}{P4CLIENT:-}" ]; then
                  _agent="%teamcity.agent.name%"
                  P4CLIENT="tc-$(printf '%s' "${'$'}_agent" | tr -c 'A-Za-z0-9_-' '_')"
                  export P4CLIENT
                fi
                [ -n "${'$'}{P4USER:-}" ] && export P4USER || true
                [ -n "${'$'}{P4PORT:-}" ] && export P4PORT   || true
                echo "[vcs] P4 User: ${'$'}{P4USER:-}"
                echo "[vcs] P4 Client: ${'$'}{P4CLIENT}"
                echo "[vcs] P4 Port: ${'$'}{P4PORT:-}"
                echo "[vcs] P4 Stream: ${p4Stream}"
                # Login using TeamCity secured password parameter; fallback to env P4PASSWD if present
                if ! p4 login -s >/dev/null 2>&1; then
                  printf '%s' "%secure.P4PASSWD%" | p4 login -a >/dev/null 2>&1 || true
                  if [ -n "${'$'}{P4PASSWD:-}" ]; then
                    printf '%s' "${'$'}{P4PASSWD}" | p4 login -a >/dev/null 2>&1 || true
                  fi
                fi
                if ! p4 info >/dev/null 2>&1; then echo "[vcs] p4 connection failed, please check authentication"; exit 0; fi
                # Ensure P4 client exists; auto-create stream client
                if ! p4 client -o "${'$'}{P4CLIENT}" >/dev/null 2>&1; then
                  host="$(hostname)"
                  cat > __p4client.txt <<EOF
Client: ${'$'}{P4CLIENT}
${'$'}([ -n "${'$'}{P4USER:-}" ] && echo "Owner: ${'$'}{P4USER}" )
Host: ${'$'}{host}
Root: %teamcity.build.checkoutDir%
Options: noallwrite clobber nocompress unlocked nomodtime rmdir
LineEnd: local
Stream: ${p4Stream}
Type: stream
EOF
                  sed -i.bak '/^$/d' __p4client.txt 2>/dev/null || true
                  rm -f __p4client.txt.bak 2>/dev/null || true
                  if ! p4 client -f -i < __p4client.txt >/dev/null 2>&1; then echo "[vcs] failed to create stream client '${'$'}{P4CLIENT}'"; rm -f __p4client.txt; exit 0; fi
                  rm -f __p4client.txt
                  p4 client -o "${'$'}{P4CLIENT}" >/dev/null 2>&1 || { echo "[vcs] stream client verification failed for '${'$'}{P4CLIENT}'"; exit 0; }
                fi
                if ! p4 reconcile ${VcsConfig.Perforce.RECONCILE_FLAGS} >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_RECONCILE_FAILED}"; exit 0; fi
                if [ "$(p4 opened ${VcsConfig.Perforce.OPENED_CHECK_FLAGS} 2>/dev/null | wc -l | tr -d ' ')" = "0" ]; then echo "${VcsConfig.Messages.NOTHING_TO_SUBMIT}"; exit 0; fi
                if ! p4 submit -d "${VcsConfig.Perforce.SUBMIT_DESCRIPTION_TEMPLATE}" >/dev/null 2>&1; then echo "${VcsConfig.Messages.P4_SUBMIT_FAILED}"; exit 0; fi
                echo "[vcs] P4 submit completed successfully"
            """.trimIndent()
        }
    }
}
