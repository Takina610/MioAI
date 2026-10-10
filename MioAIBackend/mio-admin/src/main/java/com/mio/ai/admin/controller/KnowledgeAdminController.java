package com.mio.ai.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.admin.model.dto.KnowledgeAdminUpdateRequest;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.document.DocumentQueryRequest;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseQueryRequest;
import com.mio.ai.resource.model.entity.Document;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.enums.DocumentStatusEnum;
import com.mio.ai.resource.model.enums.KnowledgeBaseStatusEnum;
import com.mio.ai.resource.model.vo.knowledge.DocumentVO;
import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;
import com.mio.ai.resource.service.knowledge.DocumentService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员知识库管理接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/admin/knowledge")
public class KnowledgeAdminController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private DocumentService documentService;

    /**
     * 分页查询知识库列表（管理员）
     */
    @PostMapping("/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<KnowledgeBaseVO>> listKnowledgeByPage(@RequestBody KnowledgeBaseQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 50");

        QueryWrapper<KnowledgeBase> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(request.getName())) {
            queryWrapper.like("name", request.getName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }
        queryWrapper.orderByDesc("update_time");

        Page<KnowledgeBase> kbPage = knowledgeBaseService.page(new Page<>(current, size), queryWrapper);
        Page<KnowledgeBaseVO> voPage = new Page<>(kbPage.getCurrent(), kbPage.getSize(), kbPage.getTotal());
        List<KnowledgeBaseVO> voList = kbPage.getRecords().stream().map(kb -> {
            KnowledgeBaseVO vo = new KnowledgeBaseVO();
            BeanUtil.copyProperties(kb, vo);
            KnowledgeBaseStatusEnum statusEnum = KnowledgeBaseStatusEnum.getByCode(kb.getStatus());
            vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return vo;
        }).toList();
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    /**
     * 根据 id 获取知识库详情
     */
    @GetMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<KnowledgeBaseVO> getKnowledgeById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "知识库 id 不合法");
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        ThrowUtils.throwIf(kb == null, ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtil.copyProperties(kb, vo);
        KnowledgeBaseStatusEnum statusEnum = KnowledgeBaseStatusEnum.getByCode(kb.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return ResultUtils.success(vo);
    }

    /**
     * 查询知识库下的文档列表（管理员）
     */
    @PostMapping("/{id}/documents")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<DocumentVO>> listDocuments(@PathVariable Long id, @RequestBody DocumentQueryRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "知识库 id 不合法");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        QueryWrapper<Document> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("kb_id", id);
        if (StrUtil.isNotBlank(request.getFileName())) {
            queryWrapper.like("file_name", request.getFileName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }
        queryWrapper.orderByDesc("create_time");

        Page<Document> docPage = documentService.page(new Page<>(request.getCurrent(), request.getPageSize()), queryWrapper);
        Page<DocumentVO> voPage = new Page<>(docPage.getCurrent(), docPage.getSize(), docPage.getTotal());
        List<DocumentVO> voList = docPage.getRecords().stream().map(doc -> {
            DocumentVO vo = new DocumentVO();
            BeanUtil.copyProperties(doc, vo);
            DocumentStatusEnum statusEnum = DocumentStatusEnum.getByCode(doc.getStatus());
            vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return vo;
        }).toList();
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    /**
     * 更新知识库信息（管理员）
     */
    @PutMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> updateKnowledge(@Valid @RequestBody KnowledgeAdminUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        KnowledgeBase kb = knowledgeBaseService.getById(request.getId());
        ThrowUtils.throwIf(kb == null, ErrorCode.NOT_FOUND_ERROR, "知识库不存在");

        KnowledgeBase update = new KnowledgeBase();
        update.setId(request.getId());
        if (request.getName() != null) {
            update.setName(request.getName());
        }
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        if (request.getIsPublic() != null) {
            update.setIsPublic(request.getIsPublic());
        }
        boolean result = knowledgeBaseService.updateById(update);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除知识库（级联清理文档、向量数据、R2 文件与智能体绑定）
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteKnowledge(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "知识库 id 不合法");
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        ThrowUtils.throwIf(kb == null, ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        boolean result = knowledgeBaseService.deleteKnowledgeBaseCascade(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除知识库下的单个文档（级联清理向量数据与 R2 文件）
     */
    @DeleteMapping("/{id}/documents/{docId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteDocument(@PathVariable Long id, @PathVariable Long docId) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "知识库 id 不合法");
        ThrowUtils.throwIf(docId == null || docId <= 0, ErrorCode.PARAMS_ERROR, "文档 id 不合法");
        Document document = documentService.getById(docId);
        ThrowUtils.throwIf(document == null, ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        ThrowUtils.throwIf(!id.equals(document.getKbId()), ErrorCode.PARAMS_ERROR, "文档不属于该知识库");
        boolean result = documentService.deleteDocumentByAdmin(docId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }
}
