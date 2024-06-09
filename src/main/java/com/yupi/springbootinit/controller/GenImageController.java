package com.yupi.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.image.GenImageAnaByAiRequest;
import com.yupi.springbootinit.model.dto.image.GenImageByAiRequest;
import com.yupi.springbootinit.model.entity.Image;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.GenImageResponse;
import com.yupi.springbootinit.model.vo.ImageResponse;
import com.yupi.springbootinit.service.ImageService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.genImage.GenImageByAiUtils;
import com.yupi.springbootinit.utils.image.AiImageUtils;
import com.yupi.springbootinit.utils.image.ImageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/image")
@Slf4j
public class GenImageController {
    @Resource
    private UserService userService;
    @Resource
    private RedisLimiterManager redisLimiterManager;

    @PostMapping("/gen/image")
    public BaseResponse<GenImageResponse> genImage(GenImageByAiRequest genImageByAiRequest, HttpServletRequest request){

        User loginUser = userService.getLoginUser(request);
        String goal = genImageByAiRequest.getGoal();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");

        String base64Image = null;
        try {
            base64Image = GenImageByAiUtils.genImageByAi(goal);
            GenImageResponse genImageResponse = new GenImageResponse();
            genImageResponse.setGenResult(base64Image);
            return ResultUtils.success(genImageResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
