/*
 * Copyright 2021
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
package de.tudarmstadt.ukp.inception.log.config;

import java.util.Set;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;

import de.tudarmstadt.ukp.clarin.webanno.api.event.AfterCasWrittenEvent;

@ConfigurationProperties("event-logging")
public class EventLoggingPropertiesImpl
    implements EventLoggingProperties
{
    private boolean enabled;

    private Set<String> excludeEvents = Set.of( //
            // Do not log this by default - hardly any information value
            AfterCasWrittenEvent.class.getSimpleName(),
            AvailabilityChangeEvent.class.getSimpleName());

    @Override
    public boolean isEnabled()
    {
        return enabled;
    }

    @Override
    public void setEnabled(boolean aEnabled)
    {
        enabled = aEnabled;
    }

    @Override
    public Set<String> getExcludeEvents()
    {
        return excludeEvents;
    }

    @Override
    public void setExcludeEvents(Set<String> aExcludeEvents)
    {
        excludeEvents = aExcludeEvents;
    }
}
