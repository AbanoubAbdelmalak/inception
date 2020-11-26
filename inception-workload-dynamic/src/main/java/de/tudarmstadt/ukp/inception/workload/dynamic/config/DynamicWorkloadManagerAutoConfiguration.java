/*
 * Copyright 2020
 * Ubiquitous Knowledge Processing (UKP) Lab
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
package de.tudarmstadt.ukp.inception.workload.dynamic.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.inception.log.EventRepository;
import de.tudarmstadt.ukp.inception.workload.dynamic.DynamicWorkloadExtension;
import de.tudarmstadt.ukp.inception.workload.dynamic.workflow.WorkflowExtension;
import de.tudarmstadt.ukp.inception.workload.dynamic.workflow.WorkflowExtensionPoint;
import de.tudarmstadt.ukp.inception.workload.dynamic.workflow.WorkflowExtensionPointImpl;
import de.tudarmstadt.ukp.inception.workload.dynamic.workflow.types.DefaultWorkflowExtension;
import de.tudarmstadt.ukp.inception.workload.dynamic.workflow.types.ExternalWorkflowExtension;
import de.tudarmstadt.ukp.inception.workload.dynamic.workflow.types.RandomizedWorkflowExtension;

@Configuration
@ConditionalOnProperty(prefix = "workload.dynamic", name = "enabled", havingValue = "true")
public class DynamicWorkloadManagerAutoConfiguration
{

    @Bean
    public DynamicWorkloadExtension dynamicWorkloadExtension()
    {
        return new DynamicWorkloadExtension();
    }

    @Bean
    public WorkflowExtensionPoint workflowExtensionPoint(List<WorkflowExtension> aWorkflowExtension)
    {
        return new WorkflowExtensionPointImpl(aWorkflowExtension);
    }

    @Bean
    @Autowired
    public ExternalWorkflowExtension curriculumWorkflowExtension(UserDao aUserService, EventRepository aEventRepository, DocumentService aDocumentService)
    {
        return new ExternalWorkflowExtension(aUserService, aEventRepository, aDocumentService);
    }

    @Bean
    public DefaultWorkflowExtension defaultWorkflowExtension()
    {
        return new DefaultWorkflowExtension();
    }

    @Bean
    public RandomizedWorkflowExtension randomizedWorkflowExtension()
    {
        return new RandomizedWorkflowExtension();
    }


}
