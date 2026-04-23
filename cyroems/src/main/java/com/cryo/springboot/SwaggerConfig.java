package com.cryo.springboot;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@SecurityScheme(type = SecuritySchemeType.HTTP,  name = "Authorization",  scheme = "bearer", in = SecuritySchemeIn.HEADER)
@Configuration
@OpenAPIDefinition(info = @Info(title = "CRYO-EM API", version = "1.0.0"), security = { @SecurityRequirement(name = "Authorization") })
public class SwaggerConfig
{
}
