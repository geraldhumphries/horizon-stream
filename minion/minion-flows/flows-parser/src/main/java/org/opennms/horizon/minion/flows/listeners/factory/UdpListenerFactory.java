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

package org.opennms.horizon.minion.flows.listeners.factory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.codahale.metrics.MetricRegistry;

import org.opennms.horizon.minion.flows.listeners.Listener;
import org.opennms.horizon.minion.flows.listeners.Parser;
import org.opennms.horizon.minion.flows.listeners.UdpListener;
import org.opennms.horizon.minion.flows.listeners.UdpParser;

public class UdpListenerFactory implements ListenerFactory {

    private final TelemetryRegistry telemetryRegistry;

    public UdpListenerFactory(TelemetryRegistry telemetryRegistry) {
        this.telemetryRegistry = Objects.requireNonNull(telemetryRegistry);
    }

    @Override
    public Class<? extends Listener> getBeanClass() {
        return UdpListener.class;
    }

    @Override
    public Listener createBean(ListenerDefinition listenerDefinition) {
        // Ensure each defined parser is of type UdpParser
        final List<Parser> parsers = listenerDefinition.getParsers().stream()
                .map(telemetryRegistry::getParser)
                .collect(Collectors.toList());
        final List<Parser> udpParsers = parsers.stream()
                .filter(p -> p instanceof UdpParser)
                .map(p -> (UdpParser) p).collect(Collectors.toList());
        if (parsers.size() != udpParsers.size()) {
            throw new IllegalArgumentException("Each parser must be of type UdpParser but was not: " + parsers);
        }
        return new UdpListener(listenerDefinition.getName(), udpParsers, new MetricRegistry());
    }
}
