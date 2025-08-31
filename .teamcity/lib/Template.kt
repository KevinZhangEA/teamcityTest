package lib

import jetbrains.buildServer.configs.kotlin.Template
import jetbrains.buildServer.configs.kotlin.VcsRoot
import lib.templates.*

// 对外统一导出（settings.kts 仍然 import lib.* 使用这些工厂）
fun defaultTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String? = null): Template       = defaultTemplateImpl(id, vcsRoot, p4Stream)
fun clientIosTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String? = null): Template     = clientIosTemplateImpl(id, vcsRoot, p4Stream)
fun clientAndroidTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String? = null): Template = clientAndroidTemplateImpl(id, vcsRoot, p4Stream)
fun serverTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String? = null): Template        = serverTemplateImpl(id, vcsRoot, p4Stream)
fun toolsTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String? = null): Template         = toolsTemplateImpl(id, vcsRoot, p4Stream)
fun assetsTemplate(id: String, vcsRoot: VcsRoot, p4Stream: String? = null): Template        = assetsTemplateImpl(id, vcsRoot, p4Stream)
