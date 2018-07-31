/*
 * Copyright 2018 Broadband Forum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.broadband_forum.obbaa.netconf.mn.fwk.server.model;

import java.util.List;

import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ExtensionDefinition;
import org.opendaylight.yangtools.yang.model.api.UnknownSchemaNode;

public enum AnvExtensions {

    MERGED_IN_PARENT_OBJECT("http://bbf.org/yang-extensions",
            "merged-in-parent-object", "2016-01-07"),
    IGNORE_DEFAULT("http://bbf.org/yang-extensions", "ignore-default", "2016-01-07"),
    TREAT_AS_RELATIVE_PATH("http://bbf.org/yang-extensions", "treat-as-relative-path",
            "2016-01-07");


    private final String m_namespace;
    private final String m_revision;
    private final String m_moduleName;

    private AnvExtensions(String nameSpace, String moduleName, String revision) {
        m_namespace = nameSpace;
        m_revision = revision;
        m_moduleName = moduleName;
    }

    private boolean isExtensionIn(ExtensionDefinition node) {
        QName qName = node.getQName();
        String namespace = qName.getNamespace().toString();
        String revision = qName.getFormattedRevision();
        String moduleName = qName.getLocalName();
        return m_namespace.equals(namespace) && m_revision.equals(revision) && m_moduleName.equals(moduleName);
    }

    public boolean isExtensionIn(DataSchemaNode dataNode) {
        List<UnknownSchemaNode> unknownSchemaNodes = dataNode.getUnknownSchemaNodes();
        for (UnknownSchemaNode unKnownSchemaNode : unknownSchemaNodes) {
            ExtensionDefinition extDef = unKnownSchemaNode.getExtensionDefinition();
            if (isExtensionIn(extDef)) {
                return true;
            }
        }
        return false;
    }

    public String getName() {
        return m_moduleName;
    }

    public String getExtensionNamespace() {
        return m_namespace;
    }

    public String getRevision() {
        return m_revision;
    }

    public static String getExtensionPrefix() {
        return "anvext";
    }
}