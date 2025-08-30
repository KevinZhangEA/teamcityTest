package lib

import jetbrains.buildServer.configs.kotlin.Template
import lib.templates.*

// 对外统一导出（settings.kts 仍然 import lib.* 使用这些工厂）
fun defaultTemplate(id: String): Template       = defaultTemplateImpl(id)
fun clientIosTemplate(id: String): Template     = clientIosTemplateImpl(id)
fun clientAndroidTemplate(id: String): Template = clientAndroidTemplateImpl(id)
fun serverTemplate(id: String): Template        = serverTemplateImpl(id)
fun toolsTemplate(id: String): Template         = toolsTemplateImpl(id)
fun assetsTemplate(id: String): Template        = assetsTemplateImpl(id)
