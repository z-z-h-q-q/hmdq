package com.hmdp.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryShopTypeList() {
        List<String> typeStringList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        if(ObjectUtil.isNotNull(typeStringList) && ObjectUtil.isNotEmpty(typeStringList)){
            List<ShopType> shopTypeList = new ArrayList<>();
            for (String typeString : typeStringList) {
                shopTypeList.add(JSONUtil.toBean(typeString, ShopType.class));
            }
            return Result.ok(shopTypeList);
        }
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        if(ObjectUtil.isNull(typeList) || ObjectUtil.isEmpty(typeList)){
            return Result.fail("类型不存在！");
        }
        for(ShopType type : typeList){
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(type));
        }
        return Result.ok(typeList);
    }
}
