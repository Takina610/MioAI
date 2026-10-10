package com.mio.ai.resource.controller.skill;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.skill.SkillAddRequest;
import com.mio.ai.resource.model.dto.skill.SkillGithubImportRequest;
import com.mio.ai.resource.model.dto.skill.SkillGithubPreviewRequest;
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.dto.skill.SkillUpdateRequest;
import com.mio.ai.resource.model.vo.skill.GithubSkillPreviewVO;
import com.mio.ai.resource.model.vo.skill.GithubSkillVO;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.service.skill.SkillGithubImportService;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能接口（用户侧）
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/skills")
public class SkillController {

    @Resource
    private SkillService skillService;

    @Resource
    private SkillGithubImportService skillGithubImportService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping
    @LogInfo
    @CacheEvict(value = "skills", allEntries = true)
    public BaseResponse<Long> addSkill(@Valid @RequestBody SkillAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.addSkill(request, userId));
    }

    @PutMapping
    @LogInfo
    @CacheEvict(value = "skills", allEntries = true)
    public BaseResponse<Boolean> updateSkill(@Valid @RequestBody SkillUpdateRequest request,
                                             HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.updateSkill(request, userId));
    }

    @DeleteMapping("/{id:\\d+}")
    @LogInfo
    @CacheEvict(value = "skills", allEntries = true)
    public BaseResponse<Boolean> deleteSkill(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.deleteSkill(id, userId));
    }

    /**
     * 技能详情（所有者或公开技能可见）
     */
    @GetMapping("/{id:\\d+}")
    public BaseResponse<SkillVO> getSkill(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.getSkillById(id, userId));
    }

    /**
     * 本人技能分页
     */
    @PostMapping("/list")
    public BaseResponse<Page<SkillVO>> listSkills(@RequestBody SkillQueryRequest request,
                                                  HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        // userId 一律以登录态为准，防止越权枚举
        request.setUserId(userId);
        return ResultUtils.success(skillService.querySkills(request));
    }

    /**
     * 公开技能分页（供智能体绑定抽屉）
     */
    @GetMapping("/market")
    @Cacheable(value = "skills")
    public BaseResponse<Page<SkillVO>> marketSkills(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "100") long size) {
        return ResultUtils.success(skillService.getPublicSkills(current, size));
    }

    /**
     * 解析 GitHub 链接，发现仓库内的技能
     */
    @PostMapping("/github/preview")
    @LogInfo
    public BaseResponse<GithubSkillPreviewVO> previewGithubSkills(
            @Valid @RequestBody SkillGithubPreviewRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillGithubImportService.preview(userId, request.getUrl()));
    }

    /**
     * 导入选中的 GitHub 技能
     */
    @PostMapping("/github/import")
    @LogInfo
    @CacheEvict(value = "skills", allEntries = true)
    public BaseResponse<List<GithubSkillVO>> importGithubSkills(
            @Valid @RequestBody SkillGithubImportRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(
                skillGithubImportService.importSkills(userId, request.getUrl(), request.getSkillPaths()));
    }
}
