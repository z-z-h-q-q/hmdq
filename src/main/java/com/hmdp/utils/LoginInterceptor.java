package com.hmdp.utils;

import cn.hutool.core.util.ObjectUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取session
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        // 2.判断用户是否存在
        if(ObjectUtil.isEmpty(user)){
            response.setStatus(401); // 未授权
            return false;
        }
        // 3.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
