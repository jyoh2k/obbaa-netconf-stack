package org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.dsm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ConfigLeafAttribute;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ModelNodeDeleteException;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ModelNodeHelperRegistry;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ModelNodeSetException;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ModelNodeWithAttributes;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.yang.YangConstraintHelper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

import org.broadband_forum.obbaa.netconf.api.messages.InsertOperation;
import org.broadband_forum.obbaa.netconf.mn.fwk.schema.SchemaRegistry;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.ModelNode;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.ModelNodeCreateException;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.ModelNodeId;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.SubSystemRegistry;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.datastore.ModelNodeDataStoreManager;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ChildListHelper;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.ModelNodeGetException;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.emn.EMNKeyUtil;

import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.util.XmlUtil;

/**
 * Created by keshava on 21/12/15.
 */
public class DsmListModelNodeHelper extends YangConstraintHelper implements ChildListHelper {

    protected final ListSchemaNode m_schemaNode;
    protected final ModelNodeHelperRegistry m_modelNodeHelperRegistry;
    protected final ModelNodeDataStoreManager m_modelNodeDSM;
    protected final SchemaRegistry m_schemaRegistry;
    protected final SubSystemRegistry m_subSystemRegistry;

    public DsmListModelNodeHelper(ListSchemaNode schemaNode, ModelNodeHelperRegistry modelNodeHelperRegistry,
            ModelNodeDataStoreManager modelNodeDSM, SchemaRegistry schemaRegistry, SubSystemRegistry subSystemRegistry) {
        super(schemaNode);
        m_schemaNode = schemaNode;
        m_modelNodeHelperRegistry = modelNodeHelperRegistry;
        m_modelNodeDSM = modelNodeDSM;
        m_schemaRegistry = schemaRegistry;
        m_subSystemRegistry = subSystemRegistry;
    }

    @Override
    public Collection<ModelNode> getValue(ModelNode parentNode, Map<QName, ConfigLeafAttribute> matchCriteria) throws ModelNodeGetException {
        ModelNodeId parentNodeId;
        if (parentNode != null) {
            parentNodeId = parentNode.getModelNodeId();
        } else {
            parentNodeId = new ModelNodeId();
        }
        Collection<ModelNode> modelNodes = XmlUtil.createCollection(m_schemaNode);
        modelNodes.addAll(m_modelNodeDSM.findNodes(m_schemaNode.getPath(), matchCriteria, parentNodeId));
        return modelNodes;
    }

    @Override
    public ModelNode addChild(ModelNode parentNode, ModelNode childNode) throws ModelNodeSetException {
        ModelNodeId parentNodeId;
        if (parentNode != null) {
            parentNodeId = parentNode.getModelNodeId();
        } else {
            parentNodeId = new ModelNodeId();
        }
        return m_modelNodeDSM.createNode(childNode, parentNodeId);
    }

    @Override
    public SchemaPath getChildModelNodeSchemaPath() {
        return m_schemaNode.getPath();
    }

    @Override
    public ModelNode addChild(ModelNode parentNode, String childUri, Map<QName, ConfigLeafAttribute> keyAttrs, Map<QName, ConfigLeafAttribute> configAttrs)
            throws ModelNodeCreateException {
        ModelNodeWithAttributes newNode = getNewModelNode(parentNode);
        Map<QName, ConfigLeafAttribute> allAttributes = new LinkedHashMap<>();
        allAttributes.putAll(keyAttrs);
        allAttributes.putAll(configAttrs);
        newNode.setAttributes(allAttributes);
        m_modelNodeDSM.createNode(newNode, parentNode.getModelNodeId());
        return newNode;
    }

    @Override
    public ModelNode addChildByUserOrder(ModelNode parentNode, Map<QName, ConfigLeafAttribute> keyAttrs,
                                         Map<QName, ConfigLeafAttribute> configAttrs, InsertOperation insertOperation, ModelNode indexNode)
            throws ModelNodeCreateException {
        ModelNodeWithAttributes childNode = getNewModelNode(parentNode);
        Map<QName, ConfigLeafAttribute> allAttributes = new LinkedHashMap<>();
        allAttributes.putAll(keyAttrs);
        allAttributes.putAll(configAttrs);
        childNode.setAttributes(allAttributes);

        return addChildByUserOrder(parentNode, childNode, indexNode, insertOperation);
    }

    @Override
    public ModelNode addChildByUserOrder(ModelNode parentNode, ModelNode childNode, ModelNode indexNode,
                                         InsertOperation insertOperation) throws ModelNodeCreateException {
        try {
            Collection<ModelNode> childList = getValue(parentNode, Collections.<QName, ConfigLeafAttribute> emptyMap());
            int insertIndex = getChildInsertIndex(childList, insertOperation, indexNode);
            m_modelNodeDSM.createNode(childNode, parentNode.getModelNodeId(), insertIndex);
        } catch (ModelNodeGetException e) {
            throw new ModelNodeCreateException("could not add child ModelNode to parent", e);
        }
        return childNode;
    }

    @Override
    public ListSchemaNode getSchemaNode() {
        return m_schemaNode;
    }

    protected int getChildInsertIndex(Collection<ModelNode> childList, InsertOperation insertOperation,
                                      ModelNode insertNodeIndex) {
    	String position = insertOperation.getName();
        if (childList.isEmpty() || position.equals(InsertOperation.FIRST)) {
            return 0;
        }
        ArrayList<ModelNode> children = (ArrayList<ModelNode>) childList;
        if (insertNodeIndex != null) {
            int indexValueToInsert = children.indexOf(insertNodeIndex);
            // For Before insert , don't need to decrease index
            if (position.equals(InsertOperation.AFTER)) {// For After insert, increase
                indexValueToInsert++;
            }
            if (indexValueToInsert > children.size() - 1) {
                return childList.size();
            } else {
                return indexValueToInsert;
            }
        } else {
            // if insertNodeIndex is null, then only InsertOperation.LAST is applicable
            return childList.size();
        }
    }

    @Override
    public void removeChild(ModelNode parentNode, ModelNode childNode) throws ModelNodeDeleteException {
        ModelNodeId parentNodeId;
        if (parentNode != null) {
            parentNodeId = parentNode.getModelNodeId();
        } else {
            parentNodeId = new ModelNodeId();
        }
        m_modelNodeDSM.removeNode(childNode, parentNodeId);
    }

    @Override
    public void removeAllChild(ModelNode parentNode) throws ModelNodeDeleteException {
        ModelNodeId grandParentId = EMNKeyUtil.getParentId(parentNode.getSchemaRegistry(), parentNode.getModelNodeSchemaPath(),
                parentNode.getModelNodeId());
        m_modelNodeDSM.removeAllNodes(parentNode, m_schemaNode.getPath(), grandParentId);
    }

    @Override
    public ModelNode createModelNode(ModelNode parentNode, Map<QName, ConfigLeafAttribute> keyAttrs) throws ModelNodeCreateException {
        ModelNodeWithAttributes childNode = getNewModelNode(parentNode);
        childNode.setAttributes(keyAttrs);
        return childNode;
    }

    protected ModelNodeWithAttributes getNewModelNode(ModelNode parentNode) {
        ModelNodeId parentNodeId;
        if (parentNode != null) {
            parentNodeId = parentNode.getModelNodeId();
        } else {
            parentNodeId = new ModelNodeId();
        }

        ModelNodeWithAttributes newNode;
        if (parentNode != null && parentNode.hasSchemaMount()) {
            newNode = new ModelNodeWithAttributes(m_schemaNode.getPath(), parentNodeId, parentNode.getMountModelNodeHelperRegistry(),
                    parentNode.getMountSubSystemRegistry(), parentNode.getMountRegistry(), m_modelNodeDSM);
        } else {
            newNode = new ModelNodeWithAttributes(m_schemaNode.getPath(), parentNodeId, m_modelNodeHelperRegistry, m_subSystemRegistry,
                    m_schemaRegistry, m_modelNodeDSM);
        }
        return newNode;
    }

    @Override
    public boolean isChildSet(ModelNode node) {
        return true;
    }

    @Override
    public boolean isChildBigList() {
        return m_modelNodeDSM.isChildTypeBigList(m_schemaNode.getPath());
    }
}
