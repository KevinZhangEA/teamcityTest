package lib

/**
 * VCS configuration settings
 * 将VCS相关的配置从代码中抽出来，便于管理和修改
 */
object VcsConfig {
    // 步骤名称配置
    object StepNames {
        const val WINDOWS = "VCS Submit (Windows)"
        const val LINUX = "VCS Submit (Linux)"
        const val MACOS = "VCS Submit (macOS)"
    }

    // Perforce配置（凭据从 UI 参数提供：env.P4USER/env.P4PORT 和 secure.P4PASSWD；P4CLIENT 可派生）
    object Perforce {
        const val SUBMIT_DESCRIPTION_TEMPLATE = "chore: submit artifacts for %build.number% [%GROUP_PATH%/%LEAF_KEY%]"
        const val CHECK_COMMAND = "p4"
        const val RECONCILE_FLAGS = "-a -e -d ."
        const val OPENED_CHECK_FLAGS = "-m1"
    }

    // 消息配置
    object Messages {
        const val P4_NOT_FOUND = "[vcs] p4 not found, skip."
        const val NOTHING_TO_SUBMIT = "[vcs] nothing to submit."
        const val P4_RECONCILE_FAILED = "[vcs] p4 reconcile failed, skip."
        const val P4_SUBMIT_FAILED = "[vcs] p4 submit failed, skip."
    }
}
