/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.recommendation.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import de.tudarmstadt.ukp.inception.recommendation.api.LayerRecommendationSupport;
import de.tudarmstadt.ukp.inception.recommendation.api.LayerRecommendtionSupportRegistry;
import de.tudarmstadt.ukp.inception.recommendation.api.model.AnnotationSuggestion;
import de.tudarmstadt.ukp.inception.support.extensionpoint.ExtensionPoint_ImplBase;

public class LayerRecommendtionSupportRegistryImpl
    extends ExtensionPoint_ImplBase<AnnotationSuggestion, LayerRecommendationSupport<?>>
    implements LayerRecommendtionSupportRegistry
{
    @Autowired
    public LayerRecommendtionSupportRegistryImpl(
            @Lazy @Autowired(required = false) List<LayerRecommendationSupport<?>> aExtensions)
    {
        super(aExtensions);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <X extends LayerRecommendationSupport<?>> Optional<X> findGenericExtension(
            AnnotationSuggestion aKey)
    {
        return getExtensions().stream() //
                .filter(e -> e.accepts(aKey)) //
                .map(e -> (X) e) //
                .findFirst();
    }
}
