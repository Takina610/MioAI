package com.mio.ai.resource.controller.model;

import com.mio.ai.bot.config.OpenCodeModelCatalog;
import com.mio.ai.bot.service.ModelConnectivityTester;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型设置：OpenCode 免费模型目录（反代 /models，-free 档）+ 供应商连通性测试。
 * <p>自定义供应商仅存浏览器本地（localStorage），后端不持久化任何供应商配置或 API Key——
 * 聊天/档位探测请求自带端点配置（瞬态使用，不落库）。连通性测试同样瞬态、不落库。
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/model")
public class ModelSettingController {

    @Resource
    private OpenCodeModelCatalog openCodeModelCatalog;

    @Resource
    private ModelConnectivityTester modelConnectivityTester;

    @Resource
    private RedisComponent redisComponent;

    /** 引擎默认模型名（免费档默认选择，游客/未设置时的回退值） */
    @Value("${spring.ai.openai.chat.model:}")
    private String engineModel;

    /** OpenCode 免费模型目录（-free 结尾，反代拉取，短缓存）+ 引擎默认模型名 */
    @GetMapping("/free")
    public BaseResponse<Map<String, Object>> freeModels() {
        List<Map<String, Object>> models = openCodeModelCatalog.listModels().stream()
                .map(m -> Map.<String, Object>of("id", m.id(), "vision", m.vision()))
                .toList();
        return ResultUtils.success(Map.of("models", models, "default", engineModel));
    }

    /** 供应商模型连通性测试：按 API 格式对真实端点发一次最小请求（瞬态，不落库） */
    @PostMapping("/provider/test")
    public BaseResponse<ModelConnectivityTester.TestResult> testProvider(
            @RequestBody @Valid ProviderTestRequest request,
            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        return ResultUtils.success(modelConnectivityTester.test(
                request.getBaseUrl().trim(), request.getApiKey() == null ? "" : request.getApiKey().trim(),
                request.getApiFormat(), request.getModel().trim()));
    }

    /** 连通性测试请求体 */
    public static class ProviderTestRequest {
        @NotBlank(message = "BaseURL 不能为空")
        @Size(max = 512)
        @Pattern(regexp = "^https?://.+", message = "BaseURL 需以 http(s):// 开头")
        private String baseUrl;

        @NotBlank(message = "模型 id 不能为空")
        @Size(max = 128)
        private String model;

        @Pattern(regexp = "^(openai|anthropic|responses)$", message = "API 格式不合法")
        private String apiFormat;

        @Size(max = 512)
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getModel() {
            return model;
        }

        public String getApiFormat() {
            return apiFormat == null || apiFormat.isBlank() ? "openai" : apiFormat;
        }

        public String getApiKey() {
            return apiKey;
        }
    }
}
