package lib

import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.VcsRoot
import lib.templates.*

// 对外统一导出（settings.kts 仍然 import lib.* 使用这些工厂）
fun defaultTemplate(id: String, vcsRoot: VcsRoot): Template       = defaultTemplateImpl(id, vcsRoot)
fun clientIosTemplate(id: String, vcsRoot: VcsRoot): Template     = clientIosTemplateImpl(id, vcsRoot)
fun clientAndroidTemplate(id: String, vcsRoot: VcsRoot): Template = clientAndroidTemplateImpl(id, vcsRoot)
fun serverTemplate(id: String, vcsRoot: VcsRoot): Template        = serverTemplateImpl(id, vcsRoot)
fun toolsTemplate(id: String, vcsRoot: VcsRoot): Template         = toolsTemplateImpl(id, vcsRoot)
fun assetsTemplate(id: String, vcsRoot: VcsRoot): Template        = assetsTemplateImpl(id, vcsRoot)
