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

package org.broadband_forum.obbaa.netconf.server.ssh.auth;

import org.broadband_forum.obbaa.netconf.api.messages.NetconfRpcError;

public class AccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private NetconfRpcError m_rpcError;

    public AccessDeniedException() {
        super();
    }

    public AccessDeniedException(NetconfRpcError rpcError) {
        super(rpcError.getErrorMessage());
        this.m_rpcError = rpcError;
    }
    
    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(Throwable cause) {
        super(cause);
    }

    public NetconfRpcError getRpcError() {
        return m_rpcError;
    }

    @Override
    public String toString() {
        if (m_rpcError != null) {
            return "AccessDeniedException [rpcError=" + m_rpcError + "]";
        }
        return super.toString();
    }

}
