import { markRaw, type Component } from 'vue'
import {
  FileExcelOutlined,
  FileExclamationOutlined,
  FileMarkdownOutlined,
  FilePdfOutlined,
  FilePptOutlined,
  FileTextOutlined,
  FileUnknownOutlined,
  FileWordOutlined,
  FileZipOutlined,
  PictureOutlined,
  SoundOutlined,
  VideoCameraOutlined
} from '@ant-design/icons-vue'
import type { AttachmentDisplay, AttachmentItem } from '@/types'

/** 浏览器 <img> 能直接渲染的图片类型 */
const IMAGE_EXTS = /\.(png|jpe?g|gif|webp|bmp|svg\??|avif|ico)$/i

export function isImageName(name: string): boolean {
  return IMAGE_EXTS.test(name)
}

/**
 * 文件类型 → 图标（尽量全覆盖：文档/表格/演示/PDF/压缩/音视频/图片/代码/数据/可执行）
 * 顺序敏感：先具体后一般（如 .csv 归表格、.ts 归代码）
 */
const ICON_RULES: Array<[RegExp, Component]> = [
  [/\.(png|jpe?g|gif|webp|bmp|svg|avif|ico|tiff?|heic|heif|psd|ai|eps|raw|cr2|nef)$/i, markRaw(PictureOutlined)],
  [/\.(mp4|mov|avi|mkv|webm|flv|wmv|m4v|mpg|mpeg|3gp|vob|ogv)$/i, markRaw(VideoCameraOutlined)],
  [/\.(mp3|wav|flac|aac|ogg|m4a|wma|opus|mid|midi|amr|aiff|caf)$/i, markRaw(SoundOutlined)],
  [/\.(zip|rar|7z|tar|gz|bz2|xz|tgz|tbz|Z|iso|dmg)$/i, markRaw(FileZipOutlined)],
  [/\.pdf$/i, markRaw(FilePdfOutlined)],
  [/\.(docx?|docm|dotx?|rtf|odt|pages|wpd|wri)$/i, markRaw(FileWordOutlined)],
  [/\.(xlsx?|xlsm|xlsb|csv|tsv|ods|numbers|prn)$/i, markRaw(FileExcelOutlined)],
  [/\.(pptx?|pptm|odp|key|ppsx?)$/i, markRaw(FilePptOutlined)],
  [/\.(md|markdown|mdx|adoc|rst|org|tex|textile|wiki)$/i, markRaw(FileMarkdownOutlined)],
  [/\.(exe|dll|so|dylib|bin|apk|app|msi|deb|rpm|jar|class|pyc|wasm|o|a|lib)$/i, markRaw(FileExclamationOutlined)],
  [/\.(txt|log|text|me)$/i, markRaw(FileTextOutlined)],
  [
    /\.(json|xml|yaml|yml|toml|ini|cfg|conf|properties|env|html?|htm|xhtml|css|scss|sass|less|styl|js|cjs|mjs|ts|mts|jsx|tsx|vue|svelte|astro|py|pyw|java|cs|c|h|cpp|cc|cxx|hpp|hh|go|rs|rb|php|swift|kt|kts|scala|dart|lua|pl|pm|r|jl|m|mm|sql|sh|bash|zsh|fish|bat|cmd|ps1|psm1|gradle|groovy|cmake|mk|dockerfile|makefile|gitignore|ipynb|graphql|proto|thrift|tf|vb|vbs|asm|s|cob|f|for|pas|d|nim|zig|v|cr|ex|exs|erl|hrl|clj|cljs|lisp|scm|hs|ml|fs|fsi|coffee|pug|jade|ejs|hbs|handlebars|twig|liquid)$/i,
    markRaw(FileTextOutlined)
  ],
  [/\.(ttf|otf|woff2?|eot)$/i, markRaw(FileTextOutlined)],
  [/\.(srt|ass|ssa|vtt|sub)$/i, markRaw(FileTextOutlined)]
]

export function attachmentIcon(name: string): Component {
  for (const [pattern, icon] of ICON_RULES) {
    if (pattern.test(name)) return icon
  }
  return FileUnknownOutlined
}

export function formatSize(size: number): string {
  if (!size) return ''
  if (size >= 1024 * 1024 * 1024) return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(size / 1024))} KB`
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

/** 消息区附件 → 展示形态：图片类型直接给沙箱下载接口作为缩略图地址 */
export function messageAttachmentDisplays(items: AttachmentItem[]): AttachmentDisplay[] {
  return items.map(item => ({
    key: item.path,
    name: item.name,
    size: item.size,
    status: 'done' as const,
    previewSrc: isImageName(item.name)
      ? `${API_BASE}/bot/attachment/download?path=${encodeURIComponent(item.path)}`
      : undefined
  }))
}
