package com.yupi.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.image.GenImageAnaByAiRequest;
import com.yupi.springbootinit.model.entity.Image;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.ImageResponse;
import com.yupi.springbootinit.service.ImageService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.image.AiImageUtils;
import com.yupi.springbootinit.utils.image.ImageUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@RestController
@RequestMapping("/image")
@Slf4j
public class ImageController {
    @Resource
    private ImageService imageService;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisLimiterManager redisLimiterManager;
    @PostMapping("/gen")
    public BaseResponse<ImageResponse> genImageAnalysis(@RequestPart("file") MultipartFile multipartFile,
                                                        GenImageAnaByAiRequest genImageAnaByAiRequest, HttpServletRequest request){

        User loginUser = userService.getLoginUser(request);
        String goal = genImageAnaByAiRequest.getGoal();
        // 校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1MB");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("png", "jpg", "svg", "webp", "jpeg");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("/image/%s/%s", loginUser.getId(), filename);

        File newFile = null;
        try {
            newFile = File.createTempFile(filepath, null);
            multipartFile.transferTo(newFile);

            Image image = new Image();
            image.setGoal(goal);
            image.setImageType(suffix);
            String base64Image = Base64.getEncoder().encodeToString(ImageUtil.readByFile(newFile));
            image.setImageData("");
            image.setUserId(loginUser.getId());
            boolean save = imageService.save(image);
            if (!save) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "添加图片失败");
            }
            AiImageUtils aiImageUtils = new AiImageUtils(redissonClient);
            String ans = aiImageUtils.getAns(newFile, goal, image.getId());
            image.setGenResult(ans);
            image.setStatus("sucessed");
            boolean update = imageService.updateById(image);
            if (!update){
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"更新图片信息失败");
            }
            ImageResponse imageResponse = new ImageResponse();
            imageResponse.setImageData(base64Image);
            imageResponse.setGenResult(ans);
            imageResponse.setImageId(image.getId());
            return ResultUtils.success(imageResponse);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            if (multipartFile != null) {
                // 删除临时文件
                boolean delete = newFile.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }

    }

}
