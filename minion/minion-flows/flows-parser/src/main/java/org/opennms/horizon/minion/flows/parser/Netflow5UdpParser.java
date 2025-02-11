/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018-2018 The OpenNMS Group, Inc.
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

package org.opennms.horizon.minion.flows.parser;

import static org.opennms.horizon.minion.flows.listeners.utils.BufferUtils.slice;

import java.net.InetSocketAddress;

import org.opennms.horizon.minion.flows.parser.factory.DnsResolver;
import org.opennms.horizon.minion.flows.parser.ie.RecordProvider;
import org.opennms.horizon.minion.flows.parser.proto.Header;
import org.opennms.horizon.minion.flows.parser.proto.Packet;
import org.opennms.horizon.minion.flows.parser.session.Session;
import org.opennms.horizon.minion.flows.parser.session.UdpSessionManager;
import org.opennms.horizon.minion.flows.parser.transport.Netflow5MessageBuilder;
import org.opennms.horizon.grpc.telemetry.contract.TelemetryMessage;
import org.opennms.horizon.shared.ipc.rpc.IpcIdentity;
import org.opennms.horizon.shared.ipc.sink.api.AsyncDispatcher;

import com.codahale.metrics.MetricRegistry;

import io.netty.buffer.ByteBuf;
import org.opennms.horizon.minion.flows.listeners.Dispatchable;
import org.opennms.horizon.minion.flows.listeners.UdpParser;
import org.opennms.horizon.minion.flows.listeners.utils.BufferUtils;

public class Netflow5UdpParser extends UdpParserBase implements UdpParser, Dispatchable {

    private final Netflow5MessageBuilder messageBuilder = new Netflow5MessageBuilder();

    public Netflow5UdpParser(final String name,
                             final AsyncDispatcher<TelemetryMessage> dispatcher,
                             final IpcIdentity identity,
                             final DnsResolver dnsResolver,
                             final MetricRegistry metricRegistry) {
        super(Protocol.NETFLOW5, name, dispatcher, identity, dnsResolver, metricRegistry);
    }

    public Netflow5MessageBuilder getMessageBuilder() {
        return this.messageBuilder;
    }

    @Override
    public boolean handles(final ByteBuf buffer) {
        return BufferUtils.uint16(buffer) == 0x0005;
    }

    @Override
    protected RecordProvider parse(final Session session, final ByteBuf buffer) throws Exception {
        final Header header = new Header(slice(buffer, Header.SIZE));
        final Packet packet = new Packet(header, buffer);

        detectClockSkew(header.unixSecs * 1000L + header.unixNSecs / 1000L, session.getRemoteAddress());

        return packet;
    }

    @Override
    protected UdpSessionManager.SessionKey buildSessionKey(final InetSocketAddress remoteAddress,
                                                           final InetSocketAddress localAddress) {
        return new Netflow9UdpParser.SessionKey(remoteAddress.getAddress(), localAddress);
    }
}
