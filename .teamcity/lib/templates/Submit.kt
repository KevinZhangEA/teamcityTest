package lib.templates

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.buildSteps.script

/**
 * Shared helpers to attach a gated "Submit to VCS" step to templates.
 * Controlled by params:
 *  - SUBMIT: true/false (default false)
 *  - SUBMIT_VCS: git | perforce (default git)
 * Optional env params for Git: GIT_USER_EMAIL, GIT_USER_NAME, GIT_PUSH_URL
 */

fun Template.addSubmitParamsDefaults() {
    params {
        param("SUBMIT", "false")
        param("SUBMIT_VCS", "git")
    }
}

fun Template.addSubmitStepWindows() {
    steps {
        script {
            name = "Submit to VCS (Windows)"
            workingDir = "%teamcity.build.checkoutDir%"
            scriptContent = """
                setlocal EnableExtensions EnableDelayedExpansion
                if /I not "%SUBMIT%"=="true" (
                  echo [submit] SUBMIT!=true, skip.
                  exit /b 0
                )
                set "_vcs=%SUBMIT_VCS%"
                if /I "%_vcs%"=="git" (
                  where git >nul 2>&1 || ( echo [submit] git not found, skip. & exit /b 0 )
                  if not defined GIT_USER_EMAIL set "GIT_USER_EMAIL=ci@example.com"
                  if not defined GIT_USER_NAME  set "GIT_USER_NAME=CI Bot"
                  git config user.email "%GIT_USER_EMAIL%"
                  git config user.name  "%GIT_USER_NAME%"
                  if exist out git add -A out
                  if exist placeholder.out git add placeholder.out
                  git diff --cached --quiet && ( echo [submit] nothing to commit. & exit /b 0 )
                  set "_msg=chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"
                  if defined GIT_PUSH_URL (
                    git commit -m "%_msg%" && git push "%GIT_PUSH_URL%" HEAD
                  ) else (
                    git commit -m "%_msg%" && git push origin HEAD
                  )
                ) else if /I "%_vcs%"=="perforce" (
                  where p4 >nul 2>&1 || ( echo [submit] p4 not found, skip. & exit /b 0 )
                  p4 reconcile -a -e -d . || ( echo [submit] p4 reconcile failed, skip. & exit /b 0 )
                  set "HASOPENED="
                  for /f "usebackq delims=" %%A in (`p4 opened -m1 2^>nul`) do set "HASOPENED=1"
                  if not defined HASOPENED (
                    echo [submit] nothing to submit.
                    exit /b 0
                  )
                  p4 submit -d "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]" || ( echo [submit] p4 submit failed, skip. & exit /b 0 )
                ) else (
                  echo [submit] unknown SUBMIT_VCS=%SUBMIT_VCS%, skip.
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
                if [ "%SUBMIT%" != "true" ]; then echo "[submit] SUBMIT!=true, skip."; exit 0; fi
                case "%SUBMIT_VCS%" in
                  git)
                    if ! command -v git >/dev/null 2>&1; then echo "[submit] git not found, skip."; exit 0; fi
                    git config user.email "${'$'}{GIT_USER_EMAIL:-ci@example.com}"
                    git config user.name  "${'$'}{GIT_USER_NAME:-CI Bot}"
                    [ -d out ] && git add -A out || true
                    [ -f placeholder.out ] && git add placeholder.out || true
                    if git diff --cached --quiet; then echo "[submit] nothing to commit."; exit 0; fi
                    msg="chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"
                    if [ -n "%GIT_PUSH_URL%" ]; then
                      git commit -m "${'$'}msg" && git push "%GIT_PUSH_URL%" HEAD
                    else
                      git commit -m "${'$'}msg" && git push origin HEAD
                    fi
                    ;;
                  perforce)
                    if ! command -v p4 >/dev/null 2>&1; then echo "[submit] p4 not found, skip."; exit 0; fi
                    if ! p4 reconcile -a -e -d .; then echo "[submit] p4 reconcile failed, skip."; exit 0; fi
                    if [ "$(p4 opened -m1 2>/dev/null | wc -l | tr -d ' ')" = "0" ]; then echo "[submit] nothing to submit."; exit 0; fi
                    p4 submit -d "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]" || { echo "[submit] p4 submit failed, skip."; exit 0; }
                    ;;
                  *)
                    echo "[submit] unknown SUBMIT_VCS=%SUBMIT_VCS%, skip."; exit 0;;
                esac
            """.trimIndent()
        }
    }
}

