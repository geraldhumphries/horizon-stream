/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

package org.opennms.horizon.minion.flows.parser.ipfix;

import static org.junit.Assert.assertEquals;
import static org.opennms.horizon.minion.flows.listeners.utils.BufferUtils.slice;

import java.net.InetAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opennms.horizon.minion.flows.parser.ipfix.proto.Header;
import org.opennms.horizon.minion.flows.parser.ipfix.proto.Packet;
import org.opennms.horizon.minion.flows.parser.session.SequenceNumberTracker;
import org.opennms.horizon.minion.flows.parser.session.Session;
import org.opennms.horizon.minion.flows.parser.session.TcpSession;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

@RunWith(Parameterized.class)
public class BlackboxTest {
    private final static Path FOLDER = Paths.get("src/test/resources/flows");

    @Parameterized.Parameters(name = "file: {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                new Object[]{Arrays.asList("ipfix.dat")},
                new Object[]{Arrays.asList("ipfix_test_openbsd_pflow_tpl.dat", "ipfix_test_openbsd_pflow_data.dat")},
                new Object[]{Arrays.asList("ipfix_test_mikrotik_tpl.dat", "ipfix_test_mikrotik_data258.dat", "ipfix_test_mikrotik_data259.dat")},
                new Object[]{Arrays.asList("ipfix_test_vmware_vds_tpl.dat", "ipfix_test_vmware_vds_data264.dat", "ipfix_test_vmware_vds_data266.dat", "ipfix_test_vmware_vds_data266_267.dat")},
                new Object[]{Arrays.asList("ipfix_test_barracuda_tpl.dat", "ipfix_test_barracuda_data256.dat")},
                new Object[]{Arrays.asList("ipfix_test_yaf_tpls_option_tpl.dat", "ipfix_test_yaf_tpl45841.dat", "ipfix_test_yaf_data45841.dat", "ipfix_test_yaf_data45873.dat", "ipfix_test_yaf_data53248.dat")}
        );
    }

    private final List<String> files;

    public BlackboxTest(final List<String> files) {
        this.files = files;
    }

    @Test
    public void testFiles() throws Exception {
        final Session session = new TcpSession(InetAddress.getLoopbackAddress(), () -> new SequenceNumberTracker(32));

        for (final String file : this.files) {

            try (final FileChannel channel = FileChannel.open(FOLDER.resolve(file))) {
                final ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
                channel.read(buffer);
                buffer.flip();

                final ByteBuf buf = Unpooled.wrappedBuffer(buffer);

                do {
                    final Header header = new Header(slice(buf, Header.SIZE));
                    final Packet packet = new Packet(session, header, slice(buf, header.length - Header.SIZE));

                    assertEquals(packet.header.versionNumber, 0x000a);
                } while (buf.isReadable());
            }
        }
    }
}
