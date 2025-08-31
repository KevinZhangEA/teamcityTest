package lib

/**
 * Submit configuration settings
 * 将submit相关的配置从代码中抽出来，便于管理和修改
 */
object SubmitConfig {
    // 默认参数配置
    object Defaults {
        const val SUBMIT = "false"
        // 移除 SUBMIT_VCS 默认值，改为根据 VCS root 自动检测
    }

    // VCS 类型检测
    object VcsDetection {
        const val GIT_VCS_TYPE = "jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot"
        const val PERFORCE_VCS_TYPE = "jetbrains.buildServer.configs.kotlin.vcs.PerforceVcsRoot"
        const val DEFAULT_VCS_TYPE = "git"  // 默认使用 git
    }

    // Git配置
    object Git {
        const val DEFAULT_USER_EMAIL = "ci@example.com"
        const val DEFAULT_USER_NAME = "CI Bot"

        // Git提交消息模板
        const val COMMIT_MESSAGE_TEMPLATE = "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"

        // Git命令检查
        const val CHECK_COMMAND = "git"

        // 支持的文件/目录
        val TRACKED_PATHS = listOf("out", "placeholder.out")
    }

    // Perforce配置
    object Perforce {
        // P4用户配置
        const val DEFAULT_USER = "buildbot"
        const val DEFAULT_CLIENT = "teamcity-client"

        // P4提交描述模板
        const val SUBMIT_DESCRIPTION_TEMPLATE = "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"

        // P4命令检查
        const val CHECK_COMMAND = "p4"

        // P4命令参数
        const val RECONCILE_FLAGS = "-a -e -d ."
        const val OPENED_CHECK_FLAGS = "-m1"
    }

    // 步骤名称配置
    object StepNames {
        const val WINDOWS = "Submit to VCS (Windows)"
        const val LINUX = "Submit to VCS (Linux)"
        const val MACOS = "Submit to VCS (macOS)"
    }

    // 消息配置
    object Messages {
        const val SUBMIT_DISABLED = "[submit] SUBMIT!=true, skip."
        const val GIT_NOT_FOUND = "[submit] git not found, skip."
        const val P4_NOT_FOUND = "[submit] p4 not found, skip."
        const val NOTHING_TO_COMMIT = "[submit] nothing to commit."
        const val NOTHING_TO_SUBMIT = "[submit] nothing to submit."
        const val P4_RECONCILE_FAILED = "[submit] p4 reconcile failed, skip."
        const val P4_SUBMIT_FAILED = "[submit] p4 submit failed, skip."
        const val VCS_AUTO_DETECTED = "[submit] VCS auto-detected: %s"
    }

    // 环境变量名称
    object EnvVars {
        // Git 环境变量
        const val GIT_USER_EMAIL = "GIT_USER_EMAIL"
        const val GIT_USER_NAME = "GIT_USER_NAME"
        const val GIT_PUSH_URL = "GIT_PUSH_URL"

        // Perforce 环境变量
        const val P4_USER = "P4USER"
        const val P4_CLIENT = "P4CLIENT"
        const val P4_PORT = "P4PORT"
        const val P4_PASSWD = "P4PASSWD"

        // 通用环境变量
        const val SUBMIT = "SUBMIT"
        const val VCS_TYPE = "VCS_TYPE"
        const val GROUP_PATH = "GROUP_PATH"
        const val LEAF_KEY = "LEAF_KEY"
        const val BUILD_NUMBER = "build.number"
    }
}
