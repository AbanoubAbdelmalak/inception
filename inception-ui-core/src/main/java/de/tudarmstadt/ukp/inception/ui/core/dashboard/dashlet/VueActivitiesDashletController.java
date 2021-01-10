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
package de.tudarmstadt.ukp.inception.ui.core.dashboard.dashlet;

import java.util.List;

public interface VueActivitiesDashletController
{
    String BASE_URL = "/de.tudarmstadt.ukp.inception.ui.core.dashboard.dashlet.VueActivitiesDashletController";
    String LIST_PATH = "/project/{projectId}/list";

    String listActivitiesUrl(long aProjectId);

    List<Activity> listActivities(long aProjectId);
}
