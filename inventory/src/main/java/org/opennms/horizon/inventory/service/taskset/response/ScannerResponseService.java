/*******************************************************************************
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
 *******************************************************************************/

package org.opennms.horizon.inventory.service.taskset.response;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opennms.horizon.azure.api.AzureScanItem;
import org.opennms.horizon.azure.api.AzureScanResponse;
import org.opennms.horizon.inventory.dto.NodeCreateDTO;
import org.opennms.horizon.inventory.model.AzureCredential;
import org.opennms.horizon.inventory.model.Node;
import org.opennms.horizon.inventory.repository.AzureCredentialRepository;
import org.opennms.horizon.inventory.repository.NodeRepository;
import org.opennms.horizon.inventory.service.NodeService;
import org.opennms.horizon.inventory.service.taskset.CollectorTaskSetService;
import org.opennms.horizon.inventory.service.taskset.MonitorTaskSetService;
import org.opennms.taskset.contract.ScanType;
import org.opennms.taskset.contract.ScannerResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScannerResponseService {
    private final AzureCredentialRepository azureCredentialRepository;
    private final NodeRepository nodeRepository;
    private final NodeService nodeService;
    private final MonitorTaskSetService monitorTaskSetService;
    private final CollectorTaskSetService collectorTaskSetService;

    public void accept(String tenantId, String location, ScannerResponse response) throws InvalidProtocolBufferException {
        Any result = response.getResult();

        switch (getType(response)) {

            // other scan types

            case AZURE: {
                AzureScanResponse azureResponse = result.unpack(AzureScanResponse.class);
                List<AzureScanItem> resultsList = azureResponse.getResultsList();

                for (int index = 0; index < resultsList.size(); index++) {
                    AzureScanItem item = resultsList.get(index);

                    // HACK: for now, creating a dummy ip address in order for status to display on ui
                    // could maybe get ip interfaces from VM to save instead but private IPs may not be unique enough if no public IP attached ?
                    // Postgres requires a valid INET field
                    String ipAddress = String.format("127.0.0.%d", index + 1);

                    processAzureScanItem(tenantId, location, ipAddress, item);
                }
                break;
            }
            case UNRECOGNIZED: {
                log.warn("Unrecognized scan type");
            }
        }
    }

    private ScanType getType(ScannerResponse response) {
        Any result = response.getResult();
        if (result.is(AzureScanResponse.class)) {
            return ScanType.AZURE;
        }
        return ScanType.UNRECOGNIZED;
    }

    private void processAzureScanItem(String tenantId, String location, String ipAddress, AzureScanItem item) {
        Optional<AzureCredential> azureCredentialOpt = azureCredentialRepository.findById(item.getCredentialId());
        if (azureCredentialOpt.isEmpty()) {
            log.warn("No Azure Credential found for id: {}", item.getCredentialId());
            return;
        }

        AzureCredential credential = azureCredentialOpt.get();

        String nodeLabel = String.format("%s (%s)", item.getName(), item.getResourceGroup());
        Optional<Node> nodeOpt = nodeRepository.findByTenantLocationAndNodeLabel(tenantId, location, nodeLabel);

        if (nodeOpt.isEmpty()) {

            //note: may need to relate AzureCredential with Node for recovery of monitoring
            NodeCreateDTO createDTO = NodeCreateDTO.newBuilder()
                .setLocation(location)
                .setManagementIp(ipAddress)
                .setLabel(nodeLabel)
                .build();
            Node node = nodeService.createNode(createDTO, tenantId);

            monitorTaskSetService.sendAzureMonitorTasks(credential, item, ipAddress, node.getId());
            collectorTaskSetService.sendAzureCollectorTasks(credential, item, ipAddress, node.getId());

        } else {
            log.warn("Node already exists for tenant: {}, location: {}, label: {}", tenantId, location, nodeLabel);
        }
    }
}
