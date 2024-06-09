package com.yupi.springbootinit.utils.sparkExcelAnalysis;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.dto.chart.AiResultDto;
import com.yupi.springbootinit.model.vo.BiResponse;
import org.redisson.api.RedissonClient;

//public class AiUtils {
//    private final RedissonClient redissonClient;
//    public AiUtils(RedissonClient redissonClient){
//        this.redissonClient = redissonClient;
//    }
//    public BiResponse getAns(long chartId, String question) {
//        BigModelChar bigModelChar = new BigModelChar(chartId,redissonClient);
//        bigModelChar.getResult(question);
//        String aReturn = bigModelChar.getReturn();
//        String chartData = "服务错误";
//        String onAnalysis = "服务错误";
//        if(aReturn.contains("：") && aReturn.contains("然后输出【【【【【"))
//            onAnalysis = aReturn.substring(aReturn.indexOf("：") + 1,aReturn.indexOf("然后输出【【【【【"));
//        String[] split = aReturn.split("```json");
//        if(split.length == 2){
//            chartData = split[1].substring(0,split[1].indexOf("```"));
//        }
//
//        BiResponse biResponse = new BiResponse();
//        biResponse.setGenChart(chartData.trim());
//        biResponse.setGenResult(onAnalysis.trim());
//
//        return biResponse;
//    }
//}
