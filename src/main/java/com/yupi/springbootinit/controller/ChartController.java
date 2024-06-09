package com.yupi.springbootinit.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.bizmq.BiMessageProducer;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.utils.SqlUtils;
import com.yupi.springbootinit.utils.sparkExcelAnalysis.AiUtils;
import com.yupi.springbootinit.utils.sparkExcelAnalysis.SparkExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 *

 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
//    @PostMapping("/list/page")
//    public BaseResponse<Page<Chart>> listChartVOByPage(@RequestBody ChartQueryRequest chartQueryRequest,
//            HttpServletRequest request) {
//        long current = chartQueryRequest.getCurrent();
//        long size = chartQueryRequest.getPageSize();
//        // 限制爬虫
//        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
//        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
//                getQueryWrapper(chartQueryRequest));
//        return ResultUtils.success(chartPage);
//    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 智能分析(同步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验目标
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 校验名称
        ThrowUtils.throwIf(StringUtils.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1MB");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("png", "jpg", "svg", "webp", "jpeg", "csv", "xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        User loginUser = userService.getLoginUser(request);

        // 限流判断
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容\n" +
//                "分析需求:\n" +
//                "{数据分析的需求或者目标\n}" +
//                "原始数据:\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = 1774372083791724546L;

        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求").append("\n");
        String usergoal = goal;
        if (StringUtils.isNotBlank(chartType)){
            usergoal += ". 请使用" + chartType;
        }
        userInput.append(usergoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 读取用户的excel文件，进行处理
        String data = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(data).append("\n");

        String result = aiManager.dochat(biModelId, userInput.toString());
        String[] splits = result.split("【【【【【");
        if (splits.length < 3){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        // 插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(data);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        return ResultUtils.success(biResponse);
    }

    /**
     * 智能分析(同步)--星火大模型
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/spark")
    public BaseResponse<BiResponse> genChartByAiAsyncSpark(@RequestPart("file") MultipartFile multipartFile,
                                                           GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验目标
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 校验名称
        ThrowUtils.throwIf(StringUtils.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1MB");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("png", "jpg", "svg", "webp", "jpeg", "csv", "xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        // 用户输入
        StringBuilder userInput = new StringBuilder();

        userInput.append("你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：");
        userInput.append("\n").append("分析需求：").append("\n").append("{").append(goal).append("}").append("\n");
        // 读取用户的excel文件，进行处理
        String data = SparkExcelUtils.excelToCsv(multipartFile);
        userInput.append("原始数据:").append("\n").append(data);
        userInput.append("请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n【【【【【\n先输出上面原始数据的分析结果：\n然后输出【【【【【\n{前端 Echarts V5 的 option 配置对象JSON代码，生成");
        userInput.append(chartType);
        userInput.append("合理地将数据进行可视化，不要生成任何多余的内容，不要注释}");

        // 插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setUserId(loginUser.getId());
        chart.setGoal(goal);
        chart.setChartData(data);
        chart.setChartType(chartType);

        // 将创建的图表保存到数据库中
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        // 限流判断
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

        // 调用ai模型
        AiUtils aiUtils = new AiUtils(redissonClient);
        BiResponse biResponse = aiUtils.getAns(chart.getId(), userInput.toString());
        chart.setGenChart(biResponse.getGenChart());
        chart.setGenResult(biResponse.getGenResult());
        chart.setStatus("sucessed");
        saveResult = chartService.updateById(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表更新失败");
        biResponse.setChartId(chart.getId());


        return ResultUtils.success(biResponse);
    }

    /**
     * 智能分析(异步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                             GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验目标
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 校验名称
        ThrowUtils.throwIf(StringUtils.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1MB");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("png", "jpg", "svg", "webp", "jpeg", "csv", "xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        User loginUser = userService.getLoginUser(request);

        // 限流判断
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容\n" +
//                "分析需求:\n" +
//                "{数据分析的需求或者目标\n}" +
//                "原始数据:\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = 1774372083791724546L;

        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求").append("\n");
        String usergoal = goal;
        if (StringUtils.isNotBlank(chartType)){
            usergoal += ". 请使用" + chartType;
        }
        userInput.append(usergoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 读取用户的excel文件，进行处理
        String data = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(data).append("\n");

        // 插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(data);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        CompletableFuture.runAsync(() -> {
            // 先修改图表任务状态为"执行中"，等执行成功后，修改为"已完成"、保存执行结果；执行失败后，修改状态为"失败",执行任务失败信息
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            if (!b){
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                return;
            };


            // 调用 AI
            String result = aiManager.dochat(biModelId, userInput.toString());
            String[] splits = result.split("【【【【【");
            if (splits.length < 3){
                handleChartUpdateError(chart.getId(), "AI生成错误");
                return;
            }
            String genChart = splits[1].trim();
            String genResult = splits[2].trim();
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setStatus("sucessed");
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult){
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            };
        }, threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析(异步消息队列)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验目标
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 校验名称
        ThrowUtils.throwIf(StringUtils.isBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        // 校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024 * 1024L;
        ThrowUtils.throwIf(size > ONE_MB, ErrorCode.PARAMS_ERROR, "文件超过1MB");
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffixList = Arrays.asList("png", "jpg", "svg", "webp", "jpeg", "csv", "xlsx");
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        User loginUser = userService.getLoginUser(request);

        // 限流判断
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

//        final String prompt = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容\n" +
//                "分析需求:\n" +
//                "{数据分析的需求或者目标\n}" +
//                "原始数据:\n" +
//                "{csv格式的原始数据，用,作为分隔符}\n" +
//                "请根据这两部分内容，按照以下格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
//                "【【【【【\n" +
//                "{前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余内容，比如注释}\n" +
//                "【【【【【\n" +
//                "{明确的数据分析结论、越详细越好，不要生成多余的注释}";
        long biModelId = CommonConstant.BI_MODEL_ID;

        // 用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求").append("\n");
        String usergoal = goal;
        if (StringUtils.isNotBlank(chartType)){
            usergoal += ". 请使用" + chartType;
        }
        userInput.append(usergoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 读取用户的excel文件，进行处理
        String data = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(data).append("\n");
        // 插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(data);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        long newChartId = chart.getId();

        biMessageProducer.sendMessage(String.valueOf(newChartId));

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());

        return ResultUtils.success(biResponse);
    }

    private void handleChartUpdateError (long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult){
            log.error("更新图表失败状态失败" + chartId + "." + execMessage);
        }
    }
    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}
