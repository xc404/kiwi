package com.kiwi.framework.permission;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Getter;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PermissionService implements InitializingBean, ApplicationContextAware
{
    private static final String permissionFile = "classpath:permission/permission.json";
    private ApplicationContext context;
    private Map<String, Permission> permissionMap = new HashMap<>();
    @Getter
    private List<Permission> permissions;

    @Override
    public void afterPropertiesSet() throws Exception {


        File file = ResourceUtils.getFile(permissionFile);
        try( var inputStream = file.toURI().toURL().openStream() ) {
            JsonNode jsonNode = JsonUtil.readTree(inputStream);
            JsonNode permissionsNode = jsonNode.path("permissions");
            List<Permission> permissions = JsonUtil.treeToValue(permissionsNode, JsonUtil.getParametricType(List.class, Permission.class));
            permissions.forEach(permission -> permissionMap.put(permission.getKey(), permission));
        }
        loadPermissionsFromContext();
        this.permissions = List.copyOf(this.permissionMap.values());
    }

    private void loadPermissionsFromContext() {
        RequestMappingHandlerMapping mapping = context.getBean(RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();
        handlerMethods.values().forEach(handlerMethod -> {
            Method method = handlerMethod.getMethod();
            if( method == null ) {
                return;
            }
            SaCheckPermission saCheckPermission = method.getAnnotation(SaCheckPermission.class);
            if( saCheckPermission == null ) {
                return;
            }
            String[] permissions = saCheckPermission.value();
            if( ArrayUtils.isEmpty(permissions) ) {
                return;
            }
            String description = "";
            if( permissions.length == 1 ) {
                Operation annotation = method.getAnnotation(Operation.class);
                description = Optional.ofNullable(annotation).map(Operation::description).orElse("");
            }
            String beanName = this.getBeanName(handlerMethod.getBean());
            for( String permission : permissions ) {
                Permission p = this.permissionMap.computeIfAbsent(permission, k -> {
                    Permission p1 = new Permission();
                    p1.setKey(permission);
                    return p1;
                });
                if( StringUtils.isBlank(p.getDescription()) ) {
                    p.setDescription(description);
                }
                p.add(beanName);
            }
        });
    }

    private String getBeanName(Object bean) {
        String[] beanNamesForType = context.getBeanNamesForType(bean.getClass());
        for( String beanName : beanNamesForType ) {
            if( context.getBean(beanName) == bean ) {
                return beanName;
            }
        }
        return bean.getClass().getSimpleName();
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

}
