import { describe, it, expect } from 'vitest'
import {
  isImageName,
  typeColor,
  formatSize,
  extLabel,
  sandboxFileUrl,
  messageAttachmentDisplays,
  MAX_PENDING_ATTACHMENTS
} from './attachmentUtils'

describe('isImageName', () => {
  it('识别常见图片扩展名', () => {
    expect(isImageName('a.png')).toBe(true)
    expect(isImageName('a.JPG')).toBe(true)
    expect(isImageName('a.jpeg')).toBe(true)
    expect(isImageName('a.webp')).toBe(true)
    expect(isImageName('a.svg')).toBe(true)
  })

  it('非图片返回 false', () => {
    expect(isImageName('a.pdf')).toBe(false)
    expect(isImageName('a.docx')).toBe(false)
    expect(isImageName('png')).toBe(false)
  })
})

describe('typeColor', () => {
  it('已知类型返回固定色', () => {
    expect(typeColor('a.xlsx')).toBe('#21A55D')
    expect(typeColor('a.pdf')).toBe('#E5483D')
    expect(typeColor('a.pptx')).toBe('#F5713D')
  })

  it('未知类型返回兜底色', () => {
    expect(typeColor('a.unknownxyz')).toBe('#98A1B3')
  })
})

describe('formatSize', () => {
  it('0 返回空串', () => {
    expect(formatSize(0)).toBe('')
  })

  it('按量级换算 GB/MB/KB', () => {
    expect(formatSize(2 * 1024 * 1024 * 1024)).toBe('2.0 GB')
    expect(formatSize(3 * 1024 * 1024)).toBe('3.0 MB')
    expect(formatSize(2048)).toBe('2 KB')
    expect(formatSize(1)).toBe('1 KB')
  })
})

describe('extLabel', () => {
  it('取大写扩展名', () => {
    expect(extLabel('project-brief.docx')).toBe('DOCX')
    expect(extLabel('a.PDF')).toBe('PDF')
  })

  it('无扩张名或过长回退为“文件”', () => {
    expect(extLabel('Makefile')).toBe('文件')
    expect(extLabel('a.verylongextension')).toBe('文件')
  })
})

describe('sandboxFileUrl', () => {
  it('对路径做 URL 编码', () => {
    expect(sandboxFileUrl('a b/c.png')).toContain('/bot/attachment/download?path=')
    expect(sandboxFileUrl('a b/c.png')).toContain('a%20b%2Fc.png')
  })
})

describe('messageAttachmentDisplays', () => {
  it('图片给缩略图地址、非图片不给', () => {
    const out = messageAttachmentDisplays([
      { path: 'p/1.png', name: '1.png', size: 100 },
      { path: 'p/2.pdf', name: '2.pdf', size: 200 }
    ])
    expect(out[0]!.previewSrc).toContain('/bot/attachment/download')
    expect(out[1]!.previewSrc).toBeUndefined()
    expect(out[0]!.status).toBe('done')
    expect(out[0]!.key).toBe('p/1.png')
  })
})

describe('MAX_PENDING_ATTACHMENTS', () => {
  it('为 50', () => {
    expect(MAX_PENDING_ATTACHMENTS).toBe(50)
  })
})
