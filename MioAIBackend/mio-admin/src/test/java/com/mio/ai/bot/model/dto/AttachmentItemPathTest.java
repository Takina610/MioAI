package com.mio.ai.bot.model.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 附件暂存路径解析纯单元测试：下载/删除的归属校验依赖 chatId 段解析正确，
 * 解析错会导致用户拿不到自己的附件，或越权访问他人附件。
 */
class AttachmentItemPathTest {

    @Test
    void extractsChatIdFromStagedPaths() {
        Assertions.assertEquals("1759900000000",
                AttachmentItem.chatIdOfStagedPath("uploads/1759900000000/ab12cd34_report.pdf"));
        Assertions.assertEquals("c1",
                AttachmentItem.chatIdOfStagedPath("outputs/c1/result.md"));
    }

    @Test
    void returnsNullForMalformedOrIllegalPaths() {
        // 段数不足（没有文件名）
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("uploads/chat1"));
        // 非暂存根
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("skills/12/SKILL.md"));
        // 穿越与绝对路径
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("uploads/../etc/passwd"));
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("/uploads/c1/a.txt"));
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("uploads/c1/../../a.txt"));
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath(null));
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("  "));
        // 空 chatId 段
        Assertions.assertNull(AttachmentItem.chatIdOfStagedPath("uploads//a.txt"));
    }

    @Test
    void stagedPathStillRequiresAllowedRoot() {
        Assertions.assertTrue(AttachmentItem.isStagedPath("uploads/c1/a.png"));
        Assertions.assertTrue(AttachmentItem.isStagedPath("outputs/c1/a.png"));
        Assertions.assertFalse(AttachmentItem.isStagedPath("memory/1/MEMORY.md"));
        Assertions.assertFalse(AttachmentItem.isStagedPath("uploads/c1/a.png".replace("uploads", "upload")));
    }
}
