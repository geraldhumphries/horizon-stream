/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
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
 *******************************************************************************/

package org.opennms.horizon.minion.flows.parser.factory;

import java.util.Objects;

import org.opennms.horizon.grpc.telemetry.contract.TelemetryMessage;
import org.opennms.horizon.minion.flows.listeners.Parser;
import org.opennms.horizon.minion.flows.listeners.factory.ParserDefinition;
import org.opennms.horizon.minion.flows.parser.FlowSinkModule;
import org.opennms.horizon.minion.flows.parser.Netflow5UdpParser;
import org.opennms.horizon.shared.ipc.rpc.IpcIdentity;
import org.opennms.horizon.shared.ipc.sink.api.AsyncDispatcher;
import org.opennms.horizon.shared.ipc.sink.api.MessageDispatcherFactory;

import com.codahale.metrics.MetricRegistry;

public class Netflow5UdpParserFactory implements ParserFactory {

    private final IpcIdentity identity;

    private final DnsResolver dnsResolver;

    private final FlowSinkModule flowSinkModule;

    private final MessageDispatcherFactory messageDispatcherFactory;


    public Netflow5UdpParserFactory(final MessageDispatcherFactory messageDispatcherFactory, final IpcIdentity identity, final DnsResolver dnsResolver,
                                    final FlowSinkModule flowSinkModule) {
        this.identity = Objects.requireNonNull(identity);
        this.dnsResolver = Objects.requireNonNull(dnsResolver);
        this.flowSinkModule = Objects.requireNonNull(flowSinkModule);
        this.messageDispatcherFactory = Objects.requireNonNull(messageDispatcherFactory);
    }

    @Override
    public Class<? extends Parser> getBeanClass() {
        return Netflow5UdpParser.class;
    }

    @Override
    public Parser createBean(final ParserDefinition parserDefinition) {
        final AsyncDispatcher<TelemetryMessage> dispatcher = messageDispatcherFactory.createAsyncDispatcher(flowSinkModule);
        return new Netflow5UdpParser(parserDefinition.getFullName(), dispatcher, identity, dnsResolver, new MetricRegistry());
    }
}
