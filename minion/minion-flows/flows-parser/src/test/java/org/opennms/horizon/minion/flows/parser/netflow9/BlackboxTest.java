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

package org.opennms.horizon.minion.flows.parser.netflow9;

import static org.junit.Assert.assertEquals;
import static org.opennms.horizon.minion.flows.listeners.utils.BufferUtils.slice;

import java.io.IOException;
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
import org.opennms.horizon.minion.flows.parser.netflow9.proto.Header;
import org.opennms.horizon.minion.flows.parser.netflow9.proto.Packet;
import org.opennms.horizon.minion.flows.parser.session.SequenceNumberTracker;
import org.opennms.horizon.minion.flows.parser.session.Session;
import org.opennms.horizon.minion.flows.parser.session.TcpSession;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

@RunWith(Parameterized.class)
public class BlackboxTest {
    private final static Path FOLDER = Paths.get("src/test/resources/flows");

    @Parameterized.Parameters(name = "file: {0}")
    public static Iterable<Object[]> data() throws IOException {
        return Arrays.asList(
            new Object[]{Arrays.asList("netflow9_test_valid01.dat")},
            new Object[]{Arrays.asList("netflow9_test_macaddr_tpl.dat", "netflow9_test_macaddr_data.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_asa_1_tpl.dat", "netflow9_test_cisco_asa_1_data.dat")},
            new Object[]{Arrays.asList("netflow9_test_nprobe_tpl.dat", "netflow9_test_softflowd_tpl_data.dat", "netflow9_test_nprobe_data.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_asa_2_tpl_26x.dat", "netflow9_test_cisco_asa_2_tpl_27x.dat", "netflow9_test_cisco_asa_2_data.dat")},
            new Object[]{Arrays.asList("netflow9_test_ubnt_edgerouter_tpl.dat", "netflow9_test_ubnt_edgerouter_data1024.dat", "netflow9_test_ubnt_edgerouter_data1025.dat")},
            new Object[]{Arrays.asList("netflow9_test_nprobe_dpi.dat")},
            new Object[]{Arrays.asList("netflow9_test_fortigate_fortios_521_tpl.dat", "netflow9_test_fortigate_fortios_521_data256.dat", "netflow9_test_fortigate_fortios_521_data257.dat")},
            new Object[]{Arrays.asList("netflow9_test_streamcore_tpl_data256.dat", "netflow9_test_streamcore_tpl_data260.dat")},
            new Object[]{Arrays.asList("netflow9_test_juniper_srx_tplopt.dat")},
            new Object[]{Arrays.asList("netflow9_test_0length_fields_tpl_data.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_asr9k_opttpl256.dat", "netflow9_test_cisco_asr9k_data256.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_asr9k_tpl260.dat", "netflow9_test_cisco_asr9k_data260.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_nbar_opttpl260.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_nbar_tpl262.dat", "netflow9_test_cisco_nbar_data262.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_wlc_tpl.dat", "netflow9_test_cisco_wlc_data261.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_wlc_8510_tpl_262.dat")},
            new Object[]{Arrays.asList("netflow9_test_cisco_1941K9.dat")},
            new Object[]{Arrays.asList("netflow9_cisco_asr1001x_tpl259.dat")},
            new Object[]{Arrays.asList("netflow9_test_paloalto_panos_tpl.dat", "netflow9_test_paloalto_panos_data.dat")},
            new Object[]{Arrays.asList("netflow9_test_juniper_data_b4_tmpl.dat")},
            new Object[]{Arrays.asList("nms-14130.dat")}
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
                    final Packet packet = new Packet(session, header, buf);

                    assertEquals(packet.header.versionNumber, 0x0009);
                } while (buf.isReadable());
            }
        }
    }
}
