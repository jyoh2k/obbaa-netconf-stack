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

package org.broadband_forum.obbaa.netconf.server.tls;

import static org.broadband_forum.obbaa.netconf.api.server.NetconfServerMessageListener.CLOSE_RESPONSE_TIME_OUT_SECS;
import static org.broadband_forum.obbaa.netconf.api.util.NetconfResources.CHUNKED_HANDLER;
import static org.broadband_forum.obbaa.netconf.api.util.NetconfResources.CHUNK_SIZE;
import static org.broadband_forum.obbaa.netconf.api.util.NetconfResources.EOM_HANDLER;
import static org.broadband_forum.obbaa.netconf.api.util.NetconfResources.MAXIMUM_SIZE_OF_CHUNKED_MESSAGES;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.w3c.dom.Document;

import org.broadband_forum.obbaa.netconf.api.LogAppNames;
import org.broadband_forum.obbaa.netconf.api.FrameAwareNetconfMessageCodec;
import org.broadband_forum.obbaa.netconf.api.FrameAwareNetconfMessageCodecImpl;
import org.broadband_forum.obbaa.netconf.api.MessageToolargeException;
import org.broadband_forum.obbaa.netconf.api.NetconfMessageCodec;
import org.broadband_forum.obbaa.netconf.api.client.NetconfClientInfo;
import org.broadband_forum.obbaa.netconf.api.logger.NetconfLogger;
import org.broadband_forum.obbaa.netconf.api.logger.NetconfLoggingContext;
import org.broadband_forum.obbaa.netconf.api.messages.AbstractNetconfRequest;
import org.broadband_forum.obbaa.netconf.api.messages.CloseSessionRequest;
import org.broadband_forum.obbaa.netconf.api.messages.CompletableMessage;
import org.broadband_forum.obbaa.netconf.api.messages.DocumentToPojoTransformer;
import org.broadband_forum.obbaa.netconf.api.messages.KillSessionRequest;
import org.broadband_forum.obbaa.netconf.api.messages.NetConfResponse;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfDelimiters;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfHelloMessage;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfRpcError;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfRpcErrorInfo;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfRpcErrorSeverity;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfRpcErrorTag;
import org.broadband_forum.obbaa.netconf.api.messages.NetconfRpcErrorType;
import org.broadband_forum.obbaa.netconf.api.messages.Notification;
import org.broadband_forum.obbaa.netconf.api.server.NetconfServerMessageListener;
import org.broadband_forum.obbaa.netconf.api.server.ResponseChannel;
import org.broadband_forum.obbaa.netconf.api.server.ServerMessageHandler;
import org.broadband_forum.obbaa.netconf.api.util.DocumentUtils;
import org.broadband_forum.obbaa.netconf.api.util.NetconfMessageBuilderException;
import org.broadband_forum.obbaa.netconf.api.util.NetconfResources;
import org.broadband_forum.obbaa.netconf.api.utils.SystemPropertyUtils;
import org.broadband_forum.obbaa.netconf.server.AbstractResponseChannel;
import org.broadband_forum.obbaa.netconf.server.AnvTracingUtil;
import org.broadband_forum.obbaa.netconf.stack.logging.AdvancedLogger;
import org.broadband_forum.obbaa.netconf.stack.logging.AdvancedLoggerUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;

public class SecureNetconfServerHandler extends SimpleChannelInboundHandler<String> {
    private static final String HELLO_MESSAGE_NOT_RECIEVED = "hello message not received";
    private static final AdvancedLogger LOGGER = AdvancedLoggerUtil.getGlobalDebugLogger(SecureNetconfServerHandler.class, LogAppNames.NETCONF_LIB);
    private NetconfServerMessageListener m_serverMessageListener;
    private AtomicBoolean m_helloRecieved = new AtomicBoolean(false);
    private Channel m_channel;
    private int m_sessionId;
    private NetconfClientInfo m_clientInfo;
    private boolean m_closeSession = false;
    private Set<String> m_caps;

    protected NetconfMessageCodec getCurrentCodec() {
        return m_currentCodec;
    }

    private FrameAwareNetconfMessageCodec m_currentCodec = new FrameAwareNetconfMessageCodecImpl();
    protected ServerMessageHandler m_serverMessageHandler;
    protected TLSChannel m_responseChannel;
    private NetconfLogger m_netconfLogger;

    public SecureNetconfServerHandler(NetconfServerMessageListener axsNetconfServerMessageListener,
                                      ServerMessageHandler serverMessageHandler, Set<String> caps, int sessionId, NetconfLogger netconfLogger) {
        this.m_serverMessageListener = axsNetconfServerMessageListener;
        this.m_serverMessageHandler = serverMessageHandler;
        this.m_sessionId = sessionId;
        this.m_clientInfo = new NetconfClientInfo("TLS-CLIENT", m_sessionId);// TODO: FNMS-9949 need to figure out how to get username
        this.m_responseChannel = new TLSChannel();
        m_netconfLogger = netconfLogger;
        this.m_caps = caps;
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        m_channel = ctx.channel();
        SocketAddress remoteAddress = m_channel.remoteAddress();
        String remoteHost = NetconfClientInfo.NON_INET_HOST;
        String remotePort = NetconfClientInfo.NON_INET_PORT;
        if (remoteAddress instanceof InetSocketAddress) {
            remoteHost = ((InetSocketAddress) remoteAddress).getAddress().getHostAddress();
            remotePort = String.valueOf(((InetSocketAddress) remoteAddress).getPort());
        }
        m_clientInfo.setRemoteHost(remoteHost).setRemotePort(remotePort);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("Channel inactive for " + ctx.name());
        super.channelInactive(ctx);
        invokeSessionClosed();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        LOGGER.debug("Server incoming message:" + msg);
        Document doc = null;
        try{
            doc = m_currentCodec.decode(msg);
            if (!m_helloRecieved.get()) {
                if (!NetconfResources.HELLO.equals(doc.getFirstChild().getNodeName())) {
                    closeSession();
                    ctx.flush();
                } else {
                    NetconfHelloMessage hello = DocumentToPojoTransformer.getHelloMessage(doc);
                    m_helloRecieved.set(true);
                    LOGGER.info("Server Received Hello message :" + hello.toString());

                    if (hello.getCapabilities().contains(NetconfResources.NETCONF_BASE_CAP_1_1)
                            && this.m_caps.contains(NetconfResources.NETCONF_BASE_CAP_1_1)) {
                        ctx.pipeline().replace(EOM_HANDLER, CHUNKED_HANDLER, new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, false, NetconfDelimiters.rpcChunkMessageDelimiter()));
                        int chunkSize = Integer.parseInt(SystemPropertyUtils.getInstance().getFromEnvOrSysProperty(CHUNK_SIZE,"65536"));
                        int maxSizeOfChunkMsg = Integer.parseInt(SystemPropertyUtils.getInstance().getFromEnvOrSysProperty(MAXIMUM_SIZE_OF_CHUNKED_MESSAGES,"67108864"));
                        m_currentCodec.useChunkedFraming(maxSizeOfChunkMsg , chunkSize);
                    }
                    m_responseChannel.setHandler(m_currentCodec);
                    m_serverMessageListener.onHello(m_clientInfo, hello.getCapabilities());
                }
            } else {
                processRequest(doc);
            }
        }catch (MessageToolargeException e){
            LOGGER.warn("Closing netconf session, incoming message too long to decode", e);
            closeSession();
            ctx.flush();
        }
    }

    private void processRequest(Document request) throws NetconfMessageBuilderException {
        NetConfResponse response = new NetConfResponse();
        AbstractNetconfRequest netconfRequest = null;
        boolean invalidRequest = false;
        try {
            String requestType = DocumentToPojoTransformer.getTypeOfNetconfRequest(request);
            if (AnvTracingUtil.isEmptyRequest(request, requestType)) {
                NetconfLoggingContext.suppress();
            }
            m_netconfLogger.logRequest(m_clientInfo.getRemoteHost(), m_clientInfo.getRemotePort(), m_clientInfo.getUsername(), new Integer(m_clientInfo.getSessionId()).toString(), request);
            String messageId = DocumentUtils.getInstance().getMessageIdFromRpcDocument(request);
            if (messageId == null || messageId.isEmpty()) {
                throw new NetconfMessageBuilderException(new NetconfRpcError(NetconfRpcErrorTag.MISSING_ATTRIBUTE, NetconfRpcErrorType.RPC,
                        NetconfRpcErrorSeverity.Error, "<message-id> cannot be null/empty")
                        .addErrorInfoElement(NetconfRpcErrorInfo.BadAttribute, NetconfResources.MESSAGE_ID)
                        .addErrorInfoElement(NetconfRpcErrorInfo.BadElement, NetconfResources.RPC).toString());
            }
            if (requestType != null) {
                switch (requestType) {
                    case NetconfResources.CLOSE_SESSION:
                        netconfRequest = DocumentToPojoTransformer.getCloseSession(request);
                        break;
                    case NetconfResources.COPY_CONFIG:
                        netconfRequest = DocumentToPojoTransformer.getCopyConfig(request);
                        break;
                    case NetconfResources.DELETE_CONFIG:
                        netconfRequest = DocumentToPojoTransformer.getDeleteConfig(request);
                        break;
                    case NetconfResources.EDIT_CONFIG:
                        netconfRequest = DocumentToPojoTransformer.getEditConfig(request);
                        break;
                    case NetconfResources.GET:
                        netconfRequest = DocumentToPojoTransformer.getGet(request);
                        break;
                    case NetconfResources.GET_CONFIG:
                        netconfRequest = DocumentToPojoTransformer.getGetConfig(request);
                        break;
                    case NetconfResources.KILL_SESSION:
                        netconfRequest = DocumentToPojoTransformer.getKillSession(request);
                        if (!isKillSessionValid((KillSessionRequest) netconfRequest, m_clientInfo)) {
                            invalidRequest = true;
                            response.setOk(false);
                            NetconfRpcError rpcError = new NetconfRpcError(NetconfRpcErrorTag.INVALID_VALUE, NetconfRpcErrorType.Application,
                                    NetconfRpcErrorSeverity.Error, "Invalid session ID");
                            response.addError(rpcError);
                        }
                        break;
                    case NetconfResources.LOCK:
                        netconfRequest = DocumentToPojoTransformer.getLockRequest(request);
                        break;
                    case NetconfResources.UNLOCK:
                        netconfRequest = DocumentToPojoTransformer.getUnLockRequest(request);
                        break;
                    case NetconfResources.CREATE_SUBSCRIPTION:
                        netconfRequest = DocumentToPojoTransformer.getCreateSubscriptionRequest(request);
                        break;
                    case NetconfResources.ACTION:
                        netconfRequest = DocumentToPojoTransformer.getAction(request);
                        break;
                    default:
                        // Could be a special rpc request
                        netconfRequest = DocumentToPojoTransformer.getRpcRequest(request);
                }

                response.setMessageId(netconfRequest.getMessageId());
            } else {
                // Send RPC error and close the session
                invalidRequest = true;
                response.setOk(false);
                NetconfRpcError rpcError = new NetconfRpcError(NetconfRpcErrorTag.MALFORMED_MESSAGE, NetconfRpcErrorType.RPC,
                        NetconfRpcErrorSeverity.Error, "Invalid request type");
                response.addError(rpcError);
                m_closeSession = true;
            }
        } catch (NetconfMessageBuilderException e) {
            if (request != null) {
                try {
                    LOGGER.error("Got an invalid netconf request from :" + m_clientInfo + " " + DocumentUtils.documentToString(request));
                } catch (NetconfMessageBuilderException ex) {
                    LOGGER.error("Got an invalid netconf request from :" + m_clientInfo, ex);
                }
            }
            invalidRequest = true;
            response.setOk(false);
            NetconfRpcError rpcError = new NetconfRpcError(NetconfRpcErrorTag.MISSING_ATTRIBUTE, NetconfRpcErrorType.RPC,
                    NetconfRpcErrorSeverity.Error, "Got an invalid netconf request from user: " + m_clientInfo.getUsername() + ", sessionId : " + m_clientInfo.getSessionId()
                    + ", IP address : " + m_clientInfo.getRemoteHost() + ", Port: " + m_clientInfo.getRemotePort());
            response.addError(rpcError);
        } finally {
            NetconfLoggingContext.enable();
        }
        if (invalidRequest) {
            m_responseChannel.sendResponse(response, netconfRequest);
        } else {
            m_serverMessageHandler.processRequest(m_clientInfo, netconfRequest, m_responseChannel);
        }
    }

    private void killSession(int sessionId) {
        LOGGER.info("Session killed :" + sessionId);
        SecureSessionManager.getInstance().killSesssion(sessionId);

    }

    private boolean isKillSessionValid(KillSessionRequest killReq, NetconfClientInfo clientInfo) {
        boolean valid = true;
        if (killReq.getSessionId().equals(clientInfo.getSessionId())) {
            valid = false;
        }
        return valid;

    }

    public void close() {
        closeSession();
    }

    private void closeSession() {
        if (m_channel != null) {
            if (m_channel.isOpen()) {
                try {
                    m_channel.close().await();
                } catch (InterruptedException e) {
                    LOGGER.error("error while closing a connection");
                }
            }
        }
    }

    protected void invokeSessionClosed() {
        m_serverMessageListener.sessionClosed("", m_sessionId);
        m_responseChannel.markSessionClosed();
        LOGGER.info("Session closed");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Error in netconf server", cause);
        // m_serverMessageListener.sessionClosed("exception caught :"+cause.getMessage(), m_sessionId);
        ctx.close();
        // m_serverMessageListener.sessionClosed("exception caught :"+cause.getMessage(), m_sessionId);
    }

    protected ResponseChannel getResponseChannel() {
        return m_responseChannel;
    }

    protected class TLSChannel extends AbstractResponseChannel {
        private NetconfMessageCodec m_handler;

        public void setHandler(NetconfMessageCodec handler) {
            m_handler = handler;
        }

        public void sendResponse(NetConfResponse response, AbstractNetconfRequest request) throws NetconfMessageBuilderException {
            markSessionToBeClosedOrKillSession(request, response);
            writeResponse(response);
        }

        private void markSessionToBeClosedOrKillSession(AbstractNetconfRequest request, NetConfResponse response) throws NetconfMessageBuilderException {
            if (request != null) {
                try {
                    response.setOtherRpcAttributes(DocumentUtils.getInstance().getRpcOtherAttributes(request.getRequestDocument()));
                    if (AnvTracingUtil.isEmptyRequest(request)) {
                        NetconfLoggingContext.suppress();
                    }
                    m_netconfLogger.logResponse(m_clientInfo.getRemoteHost(), m_clientInfo.getRemotePort(), m_clientInfo.getUsername(),
                            new Integer(m_clientInfo.getSessionId()).toString(), response.getResponseDocument(), request);
                    if (request instanceof CloseSessionRequest) {
                        if (response.isOk()) {
                            m_closeSession = true;
                        }
                    } else if (request instanceof KillSessionRequest) {
                        if (response.isOk()) {
                            killSession(((KillSessionRequest) request).getSessionId());
                        }
                    } else if (response.isCloseSession()) {
                         m_closeSession = true;
                    }
                } finally {
                    NetconfLoggingContext.enable();
                }
            }
        }

        private void writeResponse(NetConfResponse response) throws NetconfMessageBuilderException {
            Document responseDoc = response.getResponseDocument();
            byte[] responseBytes = m_handler.encode(responseDoc);
            String str = new String(responseBytes);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("SERVER: sending Response : %s", str));
            }
            try {
                doWrite(response, str);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while sending close/kill session response", e);
            } finally {
                if (m_closeSession) {
                    closeSession();
                }
            }
        }

        @Override
        public synchronized void sendNotification(Notification notification) {
            try {
                Document document = notification.getNotificationDocument();
                byte[] notifByte = m_handler.encode(document);
                String notificationString = new String(notifByte);
                // lock output Stream to make sure no other thread writes same time to support inter-leave capability
                if (m_channel.isOpen() && m_channel.isActive()) {
                    doWrite(notification, notificationString);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("A notification has been pushed to " + m_clientInfo.toString());
                    }
                } else {
                    // session is closed
                    m_closeSession = true;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Notification not pushed to " + m_clientInfo.toString() + "-session closed-" + notification.notificationToPrettyString());
                    }
                }
            } catch (Exception e) {
                // When the server fails to deliver a notification (EG TCP error), the server closes the session that created the
                // subscription
                LOGGER.error("Session will be closed. Exception occurred while sending notification-" + notification, e);
                m_closeSession = true;
            } finally {
                if (m_closeSession) {
                    closeSession();
                }
            }
        }

        private void doWrite(CompletableMessage message, String responseString) throws InterruptedException {
            try {
                ChannelFuture channelFuture = m_channel.writeAndFlush(responseString);
                channelFuture.addListener(new SecureChannelFutureListener(message, m_clientInfo));
                if (m_closeSession) {
                    channelFuture.await(CLOSE_RESPONSE_TIME_OUT_SECS, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                LOGGER.error("Error while sending the reponse to " + m_clientInfo.toString(), e);
                message.getMessageSentFuture().completeExceptionally(e);
                if (message instanceof Notification) {
                    throw e;
                }
            }
        }
    }
}

