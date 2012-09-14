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
// Generated on: 2008.09.15 at 12:57:27 PM IDT 
//

package org.apache.wink.common.model.app;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessOrder;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorOrder;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

import org.apache.wink.common.model.atom.AtomCommonAttributes;
import org.apache.wink.common.model.atom.AtomText;
import org.w3c.dom.Element;

/**
 * The "app:collection" Element Per RFC5023
 * 
 * <pre>
 * The &quot;app:collection&quot; Element
 * 
 *    The &quot;app:collection&quot; element describes a Collection.  The app:
 *    collection element MUST contain one atom:title element.
 * 
 *    The app:collection element MAY contain any number of app:accept
 *    elements, indicating the types of representations accepted by the
 *    Collection.  The order of such elements is not significant.
 * 
 *    The app:collection element MAY contain any number of app:categories
 *    elements.
 * 
 *    appCollection =
 *       element app:collection {
 *          appCommonAttributes,
 *          attribute href { atomURI  },
 *          ( atomTitle
 *            &amp; appAccept*
 *            &amp; appCategories*
 *            &amp; extensionSansTitleElement* )
 *       }
 * 
 *  o The &quot;href&quot; Attribute
 * 
 *    The app:collection element MUST contain an &quot;href&quot; attribute, whose
 *    value gives the IRI of the Collection.
 * 
 *  o The &quot;atom:title&quot; Element
 * 
 *    The &quot;atom:title&quot; element is defined in [RFC4287] and gives a human-
 *    readable title for the Collection.
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlAccessorOrder(XmlAccessOrder.UNDEFINED)
@XmlType(name = "appCollection", propOrder = {"title", "accept", "categories", "any"})
public class AppCollection extends AtomCommonAttributes {

    @XmlElement(namespace = "http://www.w3.org/2005/Atom", required = true)
    protected AtomText            title;
    protected List<AppAccept>     accept;
    protected List<AppCategories> categories;
    @XmlAnyElement
    protected List<Element>       any;
    @XmlAttribute(required = true)
    @XmlSchemaType(name = "anySimpleType")
    protected String              href;

    /**
     * Gets the value of title.
     */
    public AtomText getTitle() {
        return title;
    }

    /**
     * Sets the value of title.
     */
    public void setTitle(AtomText value) {
        this.title = value;
    }

    /**
     * Gets the accept list
     * <p>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the accept property.
     * <p>
     * For example, to add a new item, do as follows:
     * 
     * <pre>
     * getAccept().add(newItem);
     * </pre>
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link AppAccept }
     */
    public List<AppAccept> getAccept() {
        if (accept == null) {
            accept = new ArrayList<AppAccept>();
        }
        return this.accept;
    }

    /**
     * Gets the categories.
     * <p>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the categories property.
     * <p>
     * For example, to add a new item, do as follows:
     * 
     * <pre>
     * getCategories().add(newItem);
     * </pre>
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link AppCategories }
     */
    public List<AppCategories> getCategories() {
        if (categories == null) {
            categories = new ArrayList<AppCategories>();
        }
        return this.categories;
    }

    /**
     * Gets extension elements
     * <p>
     * This accessor method returns a reference to the live list, not a
     * snapshot. Therefore any modification you make to the returned list will
     * be present inside the JAXB object. This is why there is not a
     * <CODE>set</CODE> method for the any property.
     * <p>
     * For example, to add a new item, do as follows:
     * 
     * <pre>
     * getAny().add(newItem);
     * </pre>
     * <p>
     * Objects of the following type(s) are allowed in the list {@link Element }
     */
    public List<Element> getAny() {
        if (any == null) {
            any = new ArrayList<Element>();
        }
        return this.any;
    }

    /**
     * Gets the value of href.
     */
    public String getHref() {
        return href;
    }

    /**
     * Sets the value of href.
     */
    public void setHref(String value) {
        this.href = value;
    }

}