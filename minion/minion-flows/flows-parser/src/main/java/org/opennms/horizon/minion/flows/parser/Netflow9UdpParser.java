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

package org.opennms.horizon.minion.flows.parser;

import static org.opennms.horizon.minion.flows.listeners.utils.BufferUtils.slice;
import static org.opennms.horizon.minion.flows.listeners.utils.BufferUtils.uint16;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.opennms.horizon.grpc.telemetry.contract.TelemetryMessage;
import org.opennms.horizon.minion.flows.parser.factory.DnsResolver;
import org.opennms.horizon.minion.flows.parser.ie.RecordProvider;
import org.opennms.horizon.minion.flows.parser.netflow9.proto.Header;
import org.opennms.horizon.minion.flows.parser.netflow9.proto.Packet;
import org.opennms.horizon.minion.flows.parser.session.Session;
import org.opennms.horizon.minion.flows.parser.session.UdpSessionManager;
import org.opennms.horizon.minion.flows.parser.transport.Netflow9MessageBuilder;
import org.opennms.horizon.shared.ipc.rpc.IpcIdentity;
import org.opennms.horizon.shared.ipc.sink.api.AsyncDispatcher;
import org.opennms.horizon.shared.utils.InetAddressUtils;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import io.netty.buffer.ByteBuf;
import org.opennms.horizon.minion.flows.listeners.Dispatchable;
import org.opennms.horizon.minion.flows.listeners.UdpParser;

public class Netflow9UdpParser extends UdpParserBase implements UdpParser, Dispatchable {

    private final Netflow9MessageBuilder messageBuilder = new Netflow9MessageBuilder();

    public Netflow9UdpParser(final String name,
                             final AsyncDispatcher<TelemetryMessage> dispatcher,
                             final IpcIdentity identity,
                             final DnsResolver dnsResolver,
                             final MetricRegistry metricRegistry) {
        super(Protocol.NETFLOW9, name, dispatcher, identity, dnsResolver, metricRegistry);
    }

    public Netflow9MessageBuilder getMessageBuilder() {
        return this.messageBuilder;
    }

    @Override
    protected RecordProvider parse(Session session, ByteBuf buffer) throws Exception {
        final Header header = new Header(slice(buffer, Header.SIZE));
        final Packet packet = new Packet(session, header, buffer);

        detectClockSkew(header.unixSecs * 1000L, session.getRemoteAddress());

        return packet;
    }

    @Override
    public boolean handles(final ByteBuf buffer) {
        return uint16(buffer) == Header.VERSION;
    }

    @Override
    protected UdpSessionManager.SessionKey buildSessionKey(final InetSocketAddress remoteAddress, final InetSocketAddress localAddress) {
        return new SessionKey(remoteAddress.getAddress(), localAddress);
    }

    public static class SessionKey implements UdpSessionManager.SessionKey {
        private final InetAddress remoteAddress;
        private final InetSocketAddress localAddress;

        public SessionKey(final InetAddress remoteAddress, final InetSocketAddress localAddress) {
            this.remoteAddress = remoteAddress;
            this.localAddress = localAddress;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final SessionKey that = (SessionKey) o;
            return Objects.equal(this.localAddress, that.localAddress) &&
                    Objects.equal(this.remoteAddress, that.remoteAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.localAddress, this.remoteAddress);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("remoteAddress", remoteAddress)
                    .add("localAddress", localAddress)
                    .toString();
        }

        @Override
        public String getDescription() {
            return InetAddressUtils.str(this.remoteAddress);
        }

        @Override
        public InetAddress getRemoteAddress() {
            return this.remoteAddress;
        }
    }
}
