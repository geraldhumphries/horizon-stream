<template>
  <FeatherButton 
    primary
    data-test="add-node-btn" 
    @click="openModal"
  > 
    <template v-slot:icon>
      <FeatherIcon 
        :icon="Add" 
        aria-hidden="true" 
        focusable="false" 
      />
      Add Device
    </template>
  </FeatherButton>

  <PrimaryModal title="Add Device" :visible="isVisible">
    <template v-slot:content>
      <FeatherInput
        data-test="name-input"
        label="Name"
        v-model="node.label"
      />
      <FeatherSelect
        data-test="location-name-input"
        name="locationOptions"
        v-model="locationOption"
        :options="applianceQueries.locations"
        text-prop="location"
        @update:modelValue="selectLocation"
        label="Location"
      />
      <FeatherInput
        data-test="ip-input"
        label="Management IP"
        v-model="node.managementIp"
      />
      <!-- TODO: Uncomment when BE support for these available. -->
      <!-- <FeatherInput
        data-test="string-input"
        label="Community String (Optional)"
        v-model="node.snmpCommunityString"
      />
      <FeatherInput
        type="number"
        data-test="port-input"
        label="SNMP Port (Optional)"
        v-model="node.port"
      />
      <FeatherInput
        type="number"
        data-test="port-latitude"
        label="Latitude (Optional)"
        v-model="node.latitude"
      />
      <FeatherInput
        type="number"
        data-test="port-longitude"
        label="Longitude (Optional)"
        v-model="node.longitude"
      /> -->
    </template>

    <template v-slot:footer>
      <FeatherButton 
        data-test="cancel-btn" 
        secondary 
        @click="cancel">
          Cancel
      </FeatherButton>
      
      <FeatherButton 
        data-test="save-btn" 
        primary
        :disabled="!node.label" 
        @click="save">
          Save
      </FeatherButton>
    </template>
  </PrimaryModal>
</template>

<script setup lang="ts">
import Add from '@featherds/icon/action/Add'
import { useNodeMutations } from '@/store/Mutations/nodeMutations'
import { useAppliancesQueries } from '@/store/Queries/appliancesQueries'
import useModal from '@/composables/useModal'
import useSnackbar from '@/composables/useSnackbar'
import { NodeCreateInput } from '@/types/graphql'

const { showSnackbar } = useSnackbar()
const { openModal, closeModal, isVisible } = useModal()
const nodeMutations = useNodeMutations()
const applianceQueries = useAppliancesQueries()

const defaultDevice: NodeCreateInput = { 
  label: undefined,
  location: undefined,
  // latitude: undefined,
  // longitude: undefined,
  // monitoringArea: undefined,
  managementIp: undefined
  // port: undefined,
  // snmpCommunityString: undefined
}

const node = reactive({ ...defaultDevice })

const save = async () => {
  // convert the field value to undefined if their value is an empty string (entered then erased the input field returns an empty string) - empty string as value in payload causes error when adding node.
  // Object.assign(node, {
  //   port: node.port || undefined,
  //   latitude: node.latitude || undefined,
  //   longitude: node.longitude || undefined
  // })

  await nodeMutations.addNode({ node })
  
  if (!nodeMutations.error) {
    // clears node obj on successful save
    Object.assign(node, defaultDevice)

    closeModal()

    showSnackbar({
      msg: 'Device successfuly saved.'
    })

    // Timeout because node may not be available right away
    // TODO: Replace timeout with websocket/polling
    setTimeout(() => {
      applianceQueries.fetchNodesForTable()
    }, 350)
  }
}

const cancel = () => {
  // clears node obj
  Object.assign(node, defaultDevice)

  closeModal()
}

// stores the selected location option
const locationOption = ref()
// sets location val in the payload
const selectLocation = () => {
  if (locationOption.value?.location) {
    node.location = locationOption.value.location
  }
}
// sets default location when locations available
watchEffect(() => { 
  if (!node.location) {
    locationOption.value = applianceQueries.locations[0]
    selectLocation()
  }
})
</script>

<style scoped lang="scss">
@use "@featherds/styles/mixins/typography";

.title {
  @include typography.headline3;
  text-align: center;
  margin-bottom: 15px;
}
</style>
