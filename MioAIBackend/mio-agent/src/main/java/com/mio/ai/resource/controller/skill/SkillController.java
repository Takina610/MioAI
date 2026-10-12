package com.mio.ai.resource.controller.skill;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.skill.SkillGithubImportRequest;
import com.mio.ai.resource.model.dto.skill.SkillGithubPreviewRequest;
import com.mio.ai.resource.model.dto.skill.SkillPublicUpdateRequest;
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.dto.skill.SkillsShInstallRequest;
import com.mio.ai.resource.model.dto.skill.SkillsShSearchRequest;
import com.mio.ai.resource.model.vo.skill.GithubSkillPreviewVO;
import com.mio.ai.resource.model.vo.skill.GithubSkillVO;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.model.vo.skill.SkillZipInstallVO;
import com.mio.ai.resource.model.vo.skill.SkillsShSearchVO;
import com.mio.ai.resource.service.skill.SkillGithubImportService;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.resource.service.skill.SkillZipInstallService;
import com.mio.ai.resource.service.skill.SkillsShService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能接口（用户侧）。本人技能私有，自导入技能可公开进技能广场；
 *               安装来源：zip 包 / GitHub 仓库 / skills.sh / 技能广场
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
    private SkillZipInstallService skillZipInstallService;

    @Resource
    private SkillsShService skillsShService;

    @Resource
    private RedisComponent redisComponent;

    @DeleteMapping("/{id:\\d+}")
    @LogInfo
    public BaseResponse<Boolean> deleteSkill(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.deleteSkill(id, userId));
    }

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
     * 从 zip 包安装技能（包内所有含 SKILL.md 的目录各安装为一个技能）
     */
    @PostMapping("/zip")
    @LogInfo
    public BaseResponse<SkillZipInstallVO> installFromZip(@RequestParam("file") MultipartFile file,
                                                          HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillZipInstallService.installFromZip(userId, file));
    }

    /**
     * 安装技能（未安装的 GitHub 来源技能会拉取仓库内容）
     */
    @PostMapping("/{id:\\d+}/install")
    @LogInfo
    public BaseResponse<SkillVO> installSkill(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.installSkill(id, userId));
    }

    /**
     * 卸载技能（保留元数据，可重新安装）
     */
    @PostMapping("/{id:\\d+}/uninstall")
    @LogInfo
    public BaseResponse<SkillVO> uninstallSkill(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.uninstallSkill(id, userId));
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
     * 登记选中的 GitHub 技能（仅元数据，未安装态；内容在列表中点击安装时拉取）
     */
    @PostMapping("/github/import")
    @LogInfo
    public BaseResponse<List<GithubSkillVO>> importGithubSkills(
            @Valid @RequestBody SkillGithubImportRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(
                skillGithubImportService.importSkills(userId, request.getUrl(), request.getSkillPaths()));
    }

    /**
     * skills.sh 公共目录搜索
     */
    @PostMapping("/skillssh/search")
    public BaseResponse<SkillsShSearchVO> searchSkillsSh(@Valid @RequestBody SkillsShSearchRequest request,
                                                         HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillsShService.search(userId, request.getQuery().trim(),
                request.getLimit() == null ? 20 : request.getLimit(),
                request.getOffset() == null ? 0 : request.getOffset()));
    }

    /**
     * skills.sh 一键安装（在 owner/repo 内定位名为 skillId 的技能）
     */
    @PostMapping("/skillssh/install")
    @LogInfo
    public BaseResponse<Long> installSkillsSh(@Valid @RequestBody SkillsShInstallRequest request,
                                              HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillsShService.install(userId, request.getOwner(),
                request.getRepo(), request.getSkillId()));
    }

    /**
     * 技能广场分页（可匿名访问；GitHub 来源天然公开，自导入技能需本人公开）
     */
    @GetMapping("/plaza")
    public BaseResponse<Page<SkillVO>> plazaSkills(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   HttpServletRequest httpRequest) {
        Long viewerId = redisComponent.getUserIdNullable(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.queryPlazaSkills(current, size, viewerId));
    }

    /**
     * 从技能广场安装（复制一份公开技能到本人名下）
     */
    @PostMapping("/plaza/{id:\\d+}/install")
    @LogInfo
    public BaseResponse<SkillVO> installFromPlaza(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.installFromPlaza(id, userId));
    }

    /**
     * 设置自导入技能是否公开进技能广场（GitHub 来源不可设置）
     */
    @PutMapping("/{id:\\d+}/public")
    @LogInfo
    public BaseResponse<SkillVO> updateSkillPublic(@PathVariable Long id,
                                                   @Valid @RequestBody SkillPublicUpdateRequest request,
                                                   HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(skillService.updateSkillPublic(id, userId, request.getIsPublic() == 1));
    }
}
