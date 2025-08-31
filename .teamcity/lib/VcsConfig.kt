package lib

/**
 * VCS configuration settings
 * 将VCS相关的配置从代码中抽出来，便于管理和修改
 */
object VcsConfig {
    // VCS 类型检测
    object VcsDetection {
        const val GIT_VCS_TYPE = "jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot"
        const val PERFORCE_VCS_TYPE = "jetbrains.buildServer.configs.kotlin.vcs.PerforceVcsRoot"
        const val DEFAULT_VCS_TYPE = "git"
    }

    // Git配置
    object Git {
        const val DEFAULT_USER_EMAIL = "ci@example.com"
        const val DEFAULT_USER_NAME = "CI Bot"
        const val COMMIT_MESSAGE_TEMPLATE = "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"
        const val CHECK_COMMAND = "git"
        val TRACKED_PATHS = listOf("out", "placeholder.out")
    }

    // Perforce配置（凭据需从 UI 参数提供：env.P4USER/env.P4CLIENT/env.P4PORT 和 secure.P4PASSWD）
    object Perforce {
        const val SUBMIT_DESCRIPTION_TEMPLATE = "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"
        const val CHECK_COMMAND = "p4"
        const val RECONCILE_FLAGS = "-a -e -d ."
        const val OPENED_CHECK_FLAGS = "-m1"
    }

    // 步骤名称配置
    object StepNames {
        const val WINDOWS = "VCS Submit (Windows)"
        const val LINUX = "VCS Submit (Linux)"
        const val MACOS = "VCS Submit (macOS)"
    }

    // 消息配置
    object Messages {
        const val GIT_NOT_FOUND = "[vcs] git not found, skip."
        const val P4_NOT_FOUND = "[vcs] p4 not found, skip."
        const val NOTHING_TO_COMMIT = "[vcs] nothing to commit."
        const val NOTHING_TO_SUBMIT = "[vcs] nothing to submit."
        const val P4_RECONCILE_FAILED = "[vcs] p4 reconcile failed, skip."
        const val P4_SUBMIT_FAILED = "[vcs] p4 submit failed, skip."
        const val VCS_AUTO_DETECTED = "[vcs] VCS auto-detected: %s"
    }

    // 环境变量名称
    object EnvVars {
        // 检测到的 VCS 类型（git/perforce）
        const val VCS_TYPE = "VCS_TYPE"
    }
}
