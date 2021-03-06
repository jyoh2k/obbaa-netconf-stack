package org.broadband_forum.obbaa.netconf.mn.fwk.server.model.support.yang;

import org.broadband_forum.obbaa.netconf.api.util.Pair;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.AbstractSubSystem;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.FilterNode;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.GetAttributeException;
import org.broadband_forum.obbaa.netconf.mn.fwk.server.model.ModelNodeId;
import org.opendaylight.yangtools.yang.common.QName;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Used only for UTs
 */
public class LocalSubSystem extends AbstractSubSystem {

	public LocalSubSystem(){
		
	}

	@Override
	public Map<ModelNodeId, List<Element>> retrieveStateAttributes(Map<ModelNodeId, Pair<List<QName>, List<FilterNode>>> mapAttributes) throws GetAttributeException {
		return new HashMap<>();
	}
}
