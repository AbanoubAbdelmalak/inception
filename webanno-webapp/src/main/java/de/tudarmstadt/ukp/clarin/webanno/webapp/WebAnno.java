/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.webapp;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.swing.JWindow;
import javax.validation.Validator;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import de.tudarmstadt.ukp.clarin.webanno.support.standalone.LoadingSplashScreen;
import de.tudarmstadt.ukp.clarin.webanno.support.standalone.ShutdownDialogAvailableEvent;
import de.tudarmstadt.ukp.clarin.webanno.webapp.config.WebAnnoApplicationContextInitializer;
import de.tudarmstadt.ukp.clarin.webanno.webapp.config.WebAnnoBanner;

/**
 * Boots WebAnno in standalone JAR or WAR modes.
 */
@SpringBootApplication(scanBasePackages = "de.tudarmstadt.ukp.clarin.webanno")
@EntityScan(basePackages = "de.tudarmstadt.ukp.clarin.webanno")
@ImportResource({ 
        "classpath:/META-INF/application-context.xml" })
@EnableAsync
public class WebAnno
    extends SpringBootServletInitializer
{
    private static final String PROTOCOL = "AJP/1.3";
    
    @Value("${server.ajp.port:-1}")
    private int ajpPort;

    @Value("${server.ajp.secret-required:true}")
    private String ajpSecretRequired;

    @Value("${server.ajp.secret:}")
    private String ajpSecret;

    @Value("${server.ajp.address:127.0.0.1}")
    private String ajpAddress;

    @Bean
    public SessionRegistry sessionRegistry()
    {
        return new SessionRegistryImpl();
    }

    @Bean
    @Primary
    public Validator validator()
    {
        return new LocalValidatorFactoryBean();
    }
    
    // The WebAnno User model class picks this bean up by name!
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        // Set up a DelegatingPasswordEncoder which decodes legacy passwords using the
        // StandardPasswordEncoder but encodes passwords using the modern BCryptPasswordEncoder 
        String encoderForEncoding = "bcrypt";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put(encoderForEncoding, new BCryptPasswordEncoder());
        DelegatingPasswordEncoder delegatingEncoder = new DelegatingPasswordEncoder(
                encoderForEncoding, encoders);
        // Decode legacy passwords without encoder ID using the StandardPasswordEncoder
        delegatingEncoder.setDefaultPasswordEncoderForMatches(new StandardPasswordEncoder());
        return delegatingEncoder;
    }
    
    @Bean
    public EmbeddedServletContainerFactory servletContainer()
    {
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory();
        if (ajpPort > 0) {
            Connector ajpConnector = new Connector(PROTOCOL);
            ajpConnector.setPort(ajpPort);
            ajpConnector.setAttribute("secretRequired", ajpSecretRequired);
            ajpConnector.setAttribute("secret", ajpSecret);
            ajpConnector.setAttribute("address", ajpAddress);
            factory.addAdditionalTomcatConnectors(ajpConnector);
        }
        return factory;
    }

    @Override
    protected SpringApplicationBuilder createSpringApplicationBuilder()
    {
        SpringApplicationBuilder builder = super.createSpringApplicationBuilder();
        builder.properties("running.from.commandline=false");
        init(builder);
        return builder;
    }
    
    private static void init(SpringApplicationBuilder aBuilder)
    {
        aBuilder.banner(new WebAnnoBanner());
        aBuilder.initializers(new WebAnnoApplicationContextInitializer());
        aBuilder.headless(false);
        
        // Traditionally, the WebAnno configuration file is called settings.properties and is
        // either located in webanno.home or under the user's home directory. Make sure we pick
        // it up from there in addition to reading the built-in application.properties file.
        aBuilder.properties("spring.config.location="
                + "${webanno.home:${user.home}/.webanno}/settings.properties");
    }
    
    public static void main(String[] args) throws Exception
    {
        Optional<JWindow> splash = LoadingSplashScreen
                .setupScreen(WebAnno.class.getResource("splash.png"));
        
        SpringApplicationBuilder builder = new SpringApplicationBuilder();
        // Signal that we may need the shutdown dialog
        builder.properties("running.from.commandline=true");
        init(builder);
        builder.sources(WebAnno.class);
        builder.listeners(event -> {
            if (event instanceof ApplicationReadyEvent
                    || event instanceof ShutdownDialogAvailableEvent) {
                splash.ifPresent(it -> it.dispose());
            }
        });
        builder.run(args);
    }
}
