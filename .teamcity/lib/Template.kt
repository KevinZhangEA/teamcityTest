package lib

import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.VcsRoot
import templates.*

// 对外统一导出（settings.kts 仍然 import lib.* 使用这些工厂）
fun defaultTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String): Template       = defaultTemplateImpl(id, vcsRoot, p4Stream)
fun clientIosTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String): Template     = clientIosTemplateImpl(id, vcsRoot, p4Stream)
fun clientAndroidTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String): Template = clientAndroidTemplateImpl(id, vcsRoot, p4Stream)
fun serverTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String): Template        = serverTemplateImpl(id, vcsRoot, p4Stream)
fun toolsTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String): Template         = toolsTemplateImpl(id, vcsRoot, p4Stream)
fun assetsTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String): Template        = assetsTemplateImpl(id, vcsRoot, p4Stream)
