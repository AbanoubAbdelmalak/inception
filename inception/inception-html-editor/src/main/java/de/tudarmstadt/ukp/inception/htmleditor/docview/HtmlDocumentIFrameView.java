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
package de.tudarmstadt.ukp.inception.htmleditor.docview;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.IModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.inception.externaleditor.xhtml.XHtmlXmlDocumentIFrameView;

/**
 * @deprecated Use {@link XHtmlXmlDocumentIFrameView} instead
 */
@Deprecated
public class HtmlDocumentIFrameView
    extends WebMarkupContainer
{
    private static final long serialVersionUID = 4436249885266856565L;

    private @SpringBean DocumentService documentService;
    private @SpringBean HtmlDocumentViewController htmlDocumentViewController;

    private IModel<AnnotationDocument> document;

    public HtmlDocumentIFrameView(String aId, IModel<AnnotationDocument> aDoc)
    {
        super(aId);

        document = aDoc;
    }

    @Override
    protected void onComponentTag(ComponentTag aTag)
    {
        aTag.setName("iframe");
        aTag.put("src",
                htmlDocumentViewController.getDocumentUrl(document.getObject().getDocument()));
        super.onComponentTag(aTag);
    }
}
