package com.airport.maternity.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CategoryTemplateService {

    private static final String CACHE_KEY = "device_category_template";
    private static final int EXPIRATION_MINUTES = 30;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public JSONObject getCategoryTemplate() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return JSON.parseObject(cached.toString());
        }
        return initCategoryTemplate();
    }

    public JSONObject initCategoryTemplate() {
        JSONObject template = new JSONObject();
        template.put("deviceTypes", Arrays.asList("母婴室", "哺乳室", "婴儿护理台", "育婴室", "体温枪"));
        template.put("terminalAreas", Arrays.asList("T1航站楼", "T2航站楼", "T3航站楼", "国际出发区", "国内到达区", "T1值机岛", "T2值机岛"));
        redisTemplate.opsForValue().set(CACHE_KEY, template.toJSONString(), EXPIRATION_MINUTES, TimeUnit.MINUTES);
        return template;
    }

    public void refreshCategoryTemplate() {
        initCategoryTemplate();
    }

    public List<String> getDeviceTypes() {
        JSONObject template = getCategoryTemplate();
        return template.getJSONArray("deviceTypes").toJavaList(String.class);
    }

    public List<String> getTerminalAreas() {
        JSONObject template = getCategoryTemplate();
        return template.getJSONArray("terminalAreas").toJavaList(String.class);
    }
}