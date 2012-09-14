/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *  
 *******************************************************************************/
//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.1.1-b02-fcs 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2008.05.27 at 11:24:25 AM IDT 
//

package org.apache.wink.common.model.atom;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import org.apache.wink.common.model.synd.SyndCategory;

/**
 * The "atom:category" element Per RFC4287
 * 
 * <pre>
 *  The "atom:category" element conveys information about a category
 *  associated with an entry or feed.  This specification assigns no
 *  meaning to the content (if any) of this element.
 *
 *  atomCategory =
 *     element atom:category {
 *        atomCommonAttributes,
 *        attribute term { text },
 *        attribute scheme { atomUri }?,
 *        attribute label { text }?,
 *        undefinedContent
 *     }
 *     
 * o The "term" Attribute
 * 
 *    The "term" attribute is a string that identifies the category to
 *    which the entry or feed belongs.  Category elements MUST have a
 *    "term" attribute.
 * 
 * o The "scheme" Attribute
 * 
 *    The "scheme" attribute is an IRI that identifies a categorization
 *    scheme.  Category elements MAY have a "scheme" attribute.
 * 
 * o The "label" Attribute
 * 
 *    The "label" attribute provides a human-readable label for display in
 *    end-user applications.  The content of the "label" attribute is
 *    Language-Sensitive.  Entities such as "&amp;" and "&lt;" represent
 *    their corresponding characters ("&" and "<", respectively), not
 *    markup.  Category elements MAY have a "label" attribute.
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "atomCategory")
public class AtomCategory extends AtomCommonAttributes {

    @XmlAttribute(required = true)
    protected String term;
    @XmlAttribute
    protected String scheme;
    @XmlAttribute
    protected String label;

    public AtomCategory() {
    }

    public AtomCategory(SyndCategory value) {
        super(value);
        if (value == null) {
            return;
        }
        setLabel(value.getLabel());
        setScheme(value.getScheme());
        setTerm(value.getTerm());
    }

    public SyndCategory toSynd(SyndCategory value) {
        if (value == null) {
            return value;
        }
        super.toSynd(value);
        value.setLabel(getLabel());
        value.setScheme(getScheme());
        value.setTerm(getTerm());
        return value;
    }

    /**
     * Gets the value of term.
     */
    public String getTerm() {
        return term;
    }

    /**
     * Sets the value of term.
     */
    public void setTerm(String value) {
        this.term = value;
    }

    /**
     * Gets the value of scheme.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Sets the value of scheme.
     */
    public void setScheme(String value) {
        this.scheme = value;
    }

    /**
     * Gets the value of label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the value of label.
     */
    public void setLabel(String value) {
        this.label = value;
    }

}