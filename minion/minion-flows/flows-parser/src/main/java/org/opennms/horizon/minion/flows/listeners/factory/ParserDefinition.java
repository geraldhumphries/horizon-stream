/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
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

package org.opennms.horizon.minion.flows.listeners.factory;

/**
 * {@link TelemetryBeanDefinition} to define parsers.
 * Besides the common attributes, a parser must define the queue name it dispatches to.
 *
 *  @author mvrueden
 */
public interface ParserDefinition extends TelemetryBeanDefinition {
    /**
     * The name of the queue the parser "writes" to.
     *
     * @return The name of the queue the parser "writes" to. Must not be null.
     */
    String getQueueName();

    /**
     * Returns the full qualified (unique) name of the parser.
     *
     * @return The name uniquely identifying the parser. Must not be null.
     */
    String getFullName();
}
