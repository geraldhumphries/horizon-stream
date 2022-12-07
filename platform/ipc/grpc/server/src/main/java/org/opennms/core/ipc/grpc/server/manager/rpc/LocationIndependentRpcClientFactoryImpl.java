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

package org.opennms.core.ipc.grpc.server.manager.rpc;


import com.codahale.metrics.MetricRegistry;
import org.opennms.core.ipc.grpc.server.manager.LocationIndependentRpcClientFactory;
import org.opennms.core.ipc.grpc.server.manager.RpcConnectionTracker;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.horizon.core.identity.Identity;
import org.opennms.horizon.ipc.rpc.api.RpcModule;
import org.opennms.horizon.ipc.rpc.api.RpcRequest;
import org.opennms.horizon.ipc.rpc.api.RpcResponse;

public class LocationIndependentRpcClientFactoryImpl implements LocationIndependentRpcClientFactory {

    private Identity serverIdentity;
    private TracerRegistry tracerRegistry;
    private MetricRegistry rpcMetrics;
    private long ttl;

    private RpcConnectionTracker rpcConnectionTracker;


//========================================
// Getters and Setters
//----------------------------------------

    public Identity getServerIdentity() {
        return serverIdentity;
    }

    public void setServerIdentity(Identity serverIdentity) {
        this.serverIdentity = serverIdentity;
    }

    public TracerRegistry getTracerRegistry() {
        return tracerRegistry;
    }

    public void setTracerRegistry(TracerRegistry tracerRegistry) {
        this.tracerRegistry = tracerRegistry;
    }

    public MetricRegistry getRpcMetrics() {
        return rpcMetrics;
    }

    public void setRpcMetrics(MetricRegistry rpcMetrics) {
        this.rpcMetrics = rpcMetrics;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public RpcConnectionTracker getRpcConnectionTracker() {
        return rpcConnectionTracker;
    }

    public void setRpcConnectionTracker(RpcConnectionTracker rpcConnectionTracker) {
        this.rpcConnectionTracker = rpcConnectionTracker;
    }


//========================================
// Operations
//----------------------------------------

    @Override
    public <REQUEST extends RpcRequest, RESPONSE extends RpcResponse> LocationIndependentRpcClient<REQUEST, RESPONSE>
    createClient(
            RpcModule<REQUEST, RESPONSE> localModule,
            RemoteRegistrationHandler remoteRegistrationHandler
    ) {
        LocationIndependentRpcClient<REQUEST, RESPONSE> result =
                new LocationIndependentRpcClient<>(localModule, remoteRegistrationHandler);

        result.setServerIdentity(serverIdentity);
        result.setTracerRegistry(tracerRegistry);
        result.setRpcMetrics(rpcMetrics);
        result.setTtl(ttl);
        result.setRpcConnectionTracker(rpcConnectionTracker);

        return result;
    }
}
