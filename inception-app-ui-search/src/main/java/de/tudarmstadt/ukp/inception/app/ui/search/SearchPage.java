/*
 * Copyright 2017
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
package de.tudarmstadt.ukp.inception.app.ui.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.wicketstuff.annotation.mount.MountPath;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItem;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ApplicationPageBase;
import de.tudarmstadt.ukp.inception.search.SearchResult;
import de.tudarmstadt.ukp.inception.search.SearchService;

@MenuItem(icon = "images/magnifier.png", label = "Search", prio = 50)
@MountPath("/search.html")
public class SearchPage extends ApplicationPageBase
{
    private static final long serialVersionUID = 4090656233059899062L;

    private @SpringBean DocumentService documentService;
    private @SpringBean ProjectService projectService;
    private @SpringBean SearchService searchService;
    private @SpringBean UserDao userRepository;

    final WebMarkupContainer mainContainer = new WebMarkupContainer("mainContainer");

    private ListView<SourceDocument> documentList;
    private ArrayList<SourceDocument> documents = new ArrayList<SourceDocument>();

    Model<String> targetQuery = Model.of("");

    private IModel<ArrayList<Project>> projectsModel;

    private Project currentProject;
    private User currentUser;

    public SearchPage(PageParameters aParameters)
    {
        // // Initialize Mimir
        // try {
        // Mimir mimir = new Mimir(false);
        // }
        // catch (GateException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }

        currentUser = userRepository.getCurrentUser();

        ArrayList<Project> projects;

        projects = (ArrayList<Project>) projectService.listAccessibleProjects(currentUser);

        if (projects.size() > 0) {
            currentProject = projects.get(0);
        }
        else {
            currentProject = null;
        }

        projectsModel = new LoadableDetachableModel<ArrayList<Project>>()
        {
            /**
             * 
             */
            private static final long serialVersionUID = 1L;

            @Override
            protected ArrayList<Project> load()
            {
                ArrayList<Project> projects;
                // Load user's projects
                projects = (ArrayList<Project>) projectService.listAccessibleProjects(currentUser);
                return projects;
            }
        };

        add(mainContainer);

        ProjectSelectionForm projectSelectionForm = new ProjectSelectionForm(
                "projectSelectionForm");
        mainContainer.add(projectSelectionForm);

        SearchForm searchForm = new SearchForm("searchForm");

        documentList = new ListView<SourceDocument>("results", documents)
        {
            /**
             * 
             */
            private static final long serialVersionUID = -631500052426449048L;

            @Override
            protected void populateItem(ListItem<SourceDocument> item)
            {
                AjaxLink<Void> documentId = new AjaxLink<Void>("documentId")
                {
                    /**
                     * 
                     */
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget target)
                    {
                        target.appendJavaScript(
                                "alert('" + item.getModel().getObject().getId() + "');");
                    }
                };
                item.add(documentId);

                item.add(new Label("documentTitle", item.getModel().getObject().getName()));
            }
        };
        mainContainer.add(documentList);

        mainContainer.add(searchForm);

        mainContainer.setOutputMarkupId(true);
    }

    private class SearchForm
        extends Form
    {
        private static final long serialVersionUID = 2186231514180399862L;
        private TextField<String> queryField;

        public SearchForm(String id)
        {
            super(id);

            queryField = new TextField<String>("queryInput", targetQuery);

            SubmitLink submitSearch = new SubmitLink("submitSearch")
            {
                private static final long serialVersionUID = -8353553433583302935L;

                @Override
                public void onSubmit()
                {
                    if (targetQuery.getObject() == null) {
                        targetQuery.setObject(new String("*.*"));
                    }

                    searchDocuments(targetQuery.getObject());
                }
            };

            add(queryField);
            add(submitSearch);
        }
    }

    private void searchDocuments(String aQuery)
    {
        documents.clear();
        List<SearchResult> results = new ArrayList<SearchResult>();

        try {
            results = searchService.query(currentUser, currentProject, aQuery);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        for (SearchResult result : results) {
            documents.add(
                    documentService.getSourceDocument(currentProject, result.getDocumentTitle()));
        }
    }

    private class ProjectSelectionForm
        extends
        Form<Project>
    {
        public ProjectSelectionForm(String aId)
        {
            super(aId);

            DropDownChoice<Project> projectCombo = new DropDownChoice<Project>("projectCombo",
                    new PropertyModel<Project>(SearchPage.this, "currentProject"), projectsModel)
            {
                private static final long serialVersionUID = 1L;

                {
                    setChoiceRenderer(new ChoiceRenderer<Project>("name"));
                    setNullValid(false);
                }

                @Override
                protected void onSelectionChanged(Project aNewSelection)
                {
                    SearchPage.this.currentProject = aNewSelection;
                }

                @Override
                protected boolean wantOnSelectionChangedNotifications()
                {
                    return true;
                }

                @Override
                protected CharSequence getDefaultChoice(String aSelectedValue)
                {
                    return "";
                }
            };
            add(projectCombo);

        }

        private static final long serialVersionUID = -1L;

    }

}
