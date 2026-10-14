package com.mio.ai.resource.controller.log;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.vo.log.ContributionVO;
import com.mio.ai.resource.model.vo.log.TokenTrendVO;
import com.mio.ai.resource.model.vo.log.UserProfileVO;
import com.mio.ai.resource.service.log.UserInsightService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户数据洞察接口：数据画像 / 贡献图 / 每日 Token 趋势（当前用户）
 */
@Slf4j
@RestController
@RequestMapping("/usage-stats")
public class UserInsightController {

    @Resource
    private UserInsightService userInsightService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private HttpServletRequest request;

    /** 个人数据画像（跨资源的聚合统计） */
    @GetMapping("/profile")
    public BaseResponse<UserProfileVO> getProfile() {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        return ResultUtils.success(userInsightService.buildProfile(userId));
    }

    /** 贡献图：指定年份逐日 Token + 全历史五项汇总（累计/峰值/最长聊天时长/连续天数） */
    @GetMapping("/contribution")
    public BaseResponse<ContributionVO> getContribution(
            @RequestParam(required = false) Integer year) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        return ResultUtils.success(userInsightService.buildContribution(userId, year));
    }

    /** 每日 Token 趋势：按模型/智能体拆分（趋势折线与模型用量饼图共用） */
    @GetMapping("/token-trend")
    public BaseResponse<TokenTrendVO> getTokenTrend(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String dimension) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        return ResultUtils.success(userInsightService.buildTokenTrend(userId, days, dimension));
    }
}
