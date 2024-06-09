package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.model.entity.Image;
import com.yupi.springbootinit.mapper.ImageMapper;
import com.yupi.springbootinit.service.ImageService;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【image(图表信息表)】的数据库操作Service实现
* @createDate 2024-05-21 16:00:07
*/
@Service
public class ImageServiceImpl extends ServiceImpl<ImageMapper, Image>
    implements ImageService {

}




