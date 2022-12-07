/*
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 */

package org.opennms.core.ipc.grpc.server.manager.rpcstreaming.impl;

import io.grpc.stub.StreamObserver;
import org.opennms.core.ipc.grpc.common.RpcRequestProto;
import org.opennms.core.ipc.grpc.server.manager.MinionInfo;
import org.opennms.core.ipc.grpc.server.manager.MinionManager;
import org.opennms.core.ipc.grpc.server.manager.RpcConnectionTracker;
import org.opennms.core.ipc.grpc.server.manager.RpcRequestTracker;
import org.opennms.core.ipc.grpc.server.manager.adapter.InboundRpcAdapter;
import org.opennms.core.ipc.grpc.server.manager.rpcstreaming.MinionRpcStreamConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class MinionRpcStreamConnectionManagerImpl implements MinionRpcStreamConnectionManager {

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(MinionRpcStreamConnectionManagerImpl.class);

    private Logger log = DEFAULT_LOGGER;

    private final RpcConnectionTracker rpcConnectionTracker;
    private final RpcRequestTracker rpcRequestTracker;
    private final MinionManager minionManager;
    private final ExecutorService responseHandlerExecutor;


//========================================
// Constructor
//----------------------------------------

    public MinionRpcStreamConnectionManagerImpl(
            RpcConnectionTracker rpcConnectionTracker,
            RpcRequestTracker rpcRequestTracker,
            MinionManager minionManager,
            ExecutorService responseHandlerExecutor) {

        this.rpcConnectionTracker = rpcConnectionTracker;
        this.rpcRequestTracker = rpcRequestTracker;
        this.minionManager = minionManager;
        this.responseHandlerExecutor = responseHandlerExecutor;
    }


//========================================
// Lifecycle
//----------------------------------------

    public void shutdown() {
        responseHandlerExecutor.shutdown();
    }


//========================================
// Processing
//----------------------------------------

    @Override
    public InboundRpcAdapter startRpcStreaming(StreamObserver<RpcRequestProto> requestObserver) {
        MinionRpcStreamConnectionImpl connection =
                new MinionRpcStreamConnectionImpl(
                        requestObserver,
                        this::onConnectionCompleted,
                        rpcConnectionTracker,
                        rpcRequestTracker,
                        responseHandlerExecutor,
                        minionManager
                        );

        InboundRpcAdapter result =
                new InboundRpcAdapter(
                        connection::handleRpcStreamInboundMessage,
                        connection::handleRpcStreamInboundError,
                        connection::handleRpcStreamInboundCompleted
                );

        return result;
    }

//========================================
// Internals
//----------------------------------------

    private void onConnectionCompleted(StreamObserver<RpcRequestProto> streamObserver) {
            log.info("Minion RPC handler closed");
            MinionInfo removedMinionInfo = rpcConnectionTracker.removeConnection(streamObserver);

            // Notify the MinionManager of the removal
            if (removedMinionInfo.getId() != null) {
                minionManager.removeMinion(removedMinionInfo.getId());
            }
    }
}
