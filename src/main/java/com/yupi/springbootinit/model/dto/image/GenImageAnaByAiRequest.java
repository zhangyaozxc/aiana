package com.yupi.springbootinit.model.dto.image;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传请求
 *

 */
@Data
public class GenImageAnaByAiRequest implements Serializable {
    private String goal;

    private static final long serialVersionUID = 1L;
}