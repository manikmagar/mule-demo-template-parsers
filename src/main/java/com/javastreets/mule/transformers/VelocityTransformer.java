package com.javastreets.mule.transformers;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.mule.api.MuleMessage;
import org.mule.api.context.MuleContextAware;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transformer.TransformerException;
import org.mule.config.i18n.MessageFactory;
import org.mule.transformer.AbstractMessageTransformer;
import org.mule.util.IOUtils;


public class VelocityTransformer extends AbstractMessageTransformer implements MuleContextAware {

	private Log logger = LogFactory.getLog(VelocityTransformer.class);
	
	private String templatePath;
	
	public String getTemplatePath() {
		return templatePath;
	}

	public void setTemplatePath(String templatePath) {
		this.templatePath = templatePath;
	}

	@Override
	public void initialise() throws InitialisationException {
		super.initialise();
		logger.debug("Initiating VelocityTransformer");
		
		if(!Optional.of(this.getTemplatePath()).isPresent()) {
			throw new InitialisationException(MessageFactory.createStaticMessage("Missing required templatePath"), this);
		}
		
		//We will be reading template as string.
		Velocity.addProperty(RuntimeConstants.RESOURCE_LOADER, "string");
		Velocity.addProperty("string.resource.loader.class", StringResourceLoader.class.getName());
		Velocity.addProperty("string.resource.loader.modificationCheckInterval", "1");

		//Primary template may be referencing other templates on classpath
		Velocity.addProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		Velocity.addProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		
		Velocity.init();
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public Object transformMessage(MuleMessage message, String outputEncoding) throws TransformerException {
		if(logger.isDebugEnabled()) {
			logger.debug("Initiating transformation of velocity template: "+ getTemplatePath());
		}
		
		StringWriter writer = new StringWriter();
		
		StringResourceRepository repository = StringResourceLoader.getRepository();
		
		String templateContent = null;
		try {
			templateContent = IOUtils.getResourceAsString(getTemplatePath(), VelocityTransformer.class);
		} catch (IOException e) {
			logger.error("Unable to read template", e);
			throw new TransformerException(MessageFactory.createStaticMessage("Unable to read template - "+ getTemplatePath()), e);
		}
		
		//Lets run it through MEL evaluator first.
		templateContent = muleContext.getExpressionManager().parse(templateContent, message);
		repository.putStringResource(getTemplatePath(), templateContent);
		
		VelocityContext velocityContext = new VelocityContext();
		velocityContext.put("message", message);
		
		HashMap<String, Object> flowVars = new HashMap<String, Object>();
		message.getInvocationPropertyNames().forEach(key -> flowVars.put(key, message.getInvocationProperty(key)));
		
		velocityContext.put("flowVars", flowVars);
		
		Template template = Velocity.getTemplate(getTemplatePath());
		
		template.merge(velocityContext, writer);
		
		writer.flush();
		
		StringBuffer sBuffer = writer.getBuffer();
		
		//In case post velocity transformation has any MEL expressions.Eg. any resource included with #parse command.
		templateContent = muleContext.getExpressionManager().parse(sBuffer.toString(), message);
		
		message.setPayload(templateContent);
		
		return message;
	}

}
