/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package edu.cornell.mannlib.vitro.webapp.servlet.setup;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.cornell.mannlib.vitro.webapp.servlet.template.freemarker.FreeMarkerHttpServlet;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateException;

public class FreeMarkerSetup implements ServletContextListener {
    
    private static final Log log = LogFactory.getLog(FreeMarkerSetup.class);

	public void contextInitialized(ServletContextEvent event) {	

		ServletContext sc = event.getServletContext();	
		String templatePath = sc.getRealPath("/templates/freemarker");
		Configuration cfg = new Configuration();
		
		// Specify the data source where the template files come from.
		try {
			cfg.setDirectoryForTemplateLoading(new File(templatePath));
		} catch (IOException e) {
			log.error("Error specifying template directory.");
		}
		
		cfg.setTemplateUpdateDelay(0); // no template caching in development - change for production
		
		// Specify how templates will see the data-model. This is an advanced topic...
		// but just use this:
		cfg.setObjectWrapper(new DefaultObjectWrapper());
		
		try {
            cfg.setSetting("url_escaping_charset", "ISO-8859-1");
        } catch (TemplateException e) {
            // TODO Auto-generated catch block
            log.error("Error setting value for url_escaping_charset.");
        }

		FreeMarkerHttpServlet.config = cfg;
        
	}

	public void contextDestroyed(ServletContextEvent event) {
		// nothing to do here
	}

}
