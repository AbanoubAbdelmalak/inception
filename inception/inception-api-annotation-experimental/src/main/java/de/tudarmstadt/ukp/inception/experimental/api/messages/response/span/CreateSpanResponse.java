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
package de.tudarmstadt.ukp.inception.experimental.api.messages.response.span;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;

public class CreateSpanResponse
{
    private VID spanAddress;
    private String coveredText;
    private int begin;
    private int end;
    private String type;
    private String color;

    public CreateSpanResponse(VID aSpanAddress, String aCoveredText, int aBegin, int aEnd,
            String aType, String aColor)
    {
        spanAddress = aSpanAddress;
        coveredText = aCoveredText;
        begin = aBegin;
        end = aEnd;
        type = aType;
        color = aColor;
    }

    public VID getSpanAddress()
    {
        return spanAddress;
    }

    public void setSpanAddress(VID aSpanAddress)
    {
        spanAddress = aSpanAddress;
    }

    public String getCoveredText()
    {
        return coveredText;
    }

    public void setCoveredText(String aCoveredText)
    {
        coveredText = aCoveredText;
    }

    public int getBegin()
    {
        return begin;
    }

    public void setBegin(int aBegin)
    {
        begin = aBegin;
    }

    public int getEnd()
    {
        return end;
    }

    public void setEnd(int aEnd)
    {
        end = aEnd;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String aType)
    {
        type = aType;
    }

    public String getColor()
    {
        return color;
    }

    public void setColor(String aColor)
    {
        color = aColor;
    }
}
