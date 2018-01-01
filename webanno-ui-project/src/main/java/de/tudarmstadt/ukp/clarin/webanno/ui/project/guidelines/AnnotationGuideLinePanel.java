/*
 * Copyright 2012
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
package de.tudarmstadt.ukp.clarin.webanno.ui.project.guidelines;

import org.apache.wicket.model.IModel;

import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.settings.ProjectSettingsPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.settings.ProjectSettingsPanelBase;

/**
 * A Panel used to add Project Guidelines in a selected {@link Project}
 */
@ProjectSettingsPanel(label = "Guidelines", prio = 600)
public class AnnotationGuideLinePanel
    extends ProjectSettingsPanelBase
{
    private static final long serialVersionUID = 5132384175522619171L;

    public AnnotationGuideLinePanel(String aId, IModel<Project> aProjectModel)
    {
        super(aId);
        add(new ImportGuidelinesPanel("import", aProjectModel));
        add(new GuidelinesListPanel("guidelines", aProjectModel));
    }
}
