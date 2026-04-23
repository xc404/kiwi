package com.cryo.service.mail;

import java.io.StringWriter;
import java.util.Map;

public class MailTemplateRenderer
{
    public static String render(String resource, Map<String, Object> context) {

        StringBuilder renderedTemplate = new StringBuilder();

//        try( InputStream inputStream = new FileInputStream(ResourceUtils.getFile(resource)) ) {
        org.apache.velocity.VelocityContext velocityContext = new org.apache.velocity.VelocityContext(context);
        org.apache.velocity.app.VelocityEngine velocityEngine = new org.apache.velocity.app.VelocityEngine();
        velocityEngine.setProperty("resource.loader", "class");
        velocityEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.init();

        StringWriter writer = new StringWriter();
        org.apache.velocity.Template template = velocityEngine.getTemplate(resource, "UTF-8");
        template.merge(velocityContext, writer);
        renderedTemplate.append(writer);


//        } catch( IOException e ) {
//            throw new RuntimeException("Failed to render mail template", e);
//        }

        return renderedTemplate.toString();
    }
}
