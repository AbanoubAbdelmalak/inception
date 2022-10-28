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
package de.tudarmstadt.ukp.inception.externaleditor.policy;

import static de.tudarmstadt.ukp.clarin.webanno.support.SettingsUtil.getPropApplicationHome;
import static de.tudarmstadt.ukp.inception.externaleditor.policy.SafetyNetDocumentPolicy.DEFAULT_POLICY_YAML;
import static de.tudarmstadt.ukp.inception.support.xml.XmlParserUtils.makeXmlSerializer;
import static java.lang.System.setProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tudarmstadt.ukp.inception.externaleditor.config.ExternalEditorPropertiesImpl;
import de.tudarmstadt.ukp.inception.support.xml.sanitizer.SanitizingContentHandler;

class SafetyNetDocumentPolicyTest
{
    @Test
    void thatScriptBlockIsDropped() throws Exception
    {
        var properties = new ExternalEditorPropertiesImpl();
        var sut = new SafetyNetDocumentPolicy(properties);

        var buffer = new StringWriter();
        var ch = new SanitizingContentHandler(makeXmlSerializer(buffer), sut.getPolicy());

        ch.startDocument();
        ch.startElement("html");
        ch.startElement("head");
        ch.startElement("script");
        ch.characters("script in header");
        ch.endElement("script");
        ch.endElement("head");
        ch.startElement("body");
        ch.characters("content");
        ch.startElement("script");
        ch.characters("script in body");
        ch.endElement("script");
        ch.endElement("body");
        ch.endElement("html");
        ch.endDocument();

        assertThat(buffer.toString()).isEqualTo(
                "<html><head>                </head><body>content              </body></html>");
    }

    @Test
    void thatEventAttributesAreDropped() throws Exception
    {
        var properties = new ExternalEditorPropertiesImpl();
        var sut = new SafetyNetDocumentPolicy(properties);

        var buffer = new StringWriter();
        var ch = new SanitizingContentHandler(makeXmlSerializer(buffer), sut.getPolicy());

        ch.startDocument();
        ch.startElement("html");
        ch.startElement("body", Map.of("onload", "alert('hello!')"));
        ch.characters("content");
        ch.endElement("body");
        ch.endElement("html");
        ch.endDocument();

        assertThat(buffer.toString()).isEqualTo("<html><body>content</body></html>");
    }

    @Test
    void thatAttributeWithJavaScriptIsDropped() throws Exception
    {
        var properties = new ExternalEditorPropertiesImpl();
        var sut = new SafetyNetDocumentPolicy(properties);

        var buffer = new StringWriter();
        var ch = new SanitizingContentHandler(makeXmlSerializer(buffer), sut.getPolicy());

        ch.startDocument();
        ch.startElement("html");
        ch.startElement("body");
        ch.characters("content");
        ch.startElement("q", Map.of("cite", "\tjavascript: alert('boooh!')"));
        ch.endElement("q");
        ch.startElement("q", Map.of("cite", "http://dum.my"));
        ch.endElement("q");
        ch.endElement("body");
        ch.endElement("html");
        ch.endDocument();

        assertThat(buffer.toString())
                .isEqualTo("<html><body>content<q/><q cite=\"http://dum.my\"/></body></html>");
    }

    @Test
    void thatOverrideFileIsPickedUp(@TempDir Path aTemp) throws IOException
    {
        Path policyFile = aTemp.resolve(DEFAULT_POLICY_YAML);
        setProperty(getPropApplicationHome(), aTemp.toString());

        var properties = new ExternalEditorPropertiesImpl();
        var sut = new SafetyNetDocumentPolicy(properties);

        var p1 = sut.getPolicy();
        assertThat(p1.getElementPolicies()).hasSize(12);

        write(policyFile.toFile(), "policies: []", UTF_8);

        var p2 = sut.getPolicy();
        assertThat(p2.getElementPolicies()).isEmpty();

        write(policyFile.toFile(), "policies: [ {elements: [a], action: PASS}]", UTF_8);

        var p3 = sut.getPolicy();
        assertThat(p3.getElementPolicies()).hasSize(1);

        Files.delete(policyFile);

        var p4 = sut.getPolicy();
        assertThat(p4.getElementPolicies()).hasSize(12);
    }
}
