<template>
  <div class="header-container">
    <PageHeader heading="Graphs" class="header" data-test="page-header" />
    <FeatherButton text @click="onDownload" class="btn-download">
      Download All
    </FeatherButton>
  </div>
  <div id="graphs-container" v-if="store.fetchIsDone">
    <LineGraph :graph="nodeLatency" />
    <LineGraph :graph="bytesInOut" />
    <LineGraph :graph="bytesIn" />
    <LineGraph :graph="bytesOut" />
    <LineGraph :graph="hcIn" />
    <LineGraph :graph="hcOut" />
  </div>
</template>
  
<script setup lang="ts">
import { TimeRangeUnit } from '@/types/graphql'
import { GraphProps } from '@/types/graphs'
import { downloadMultipleCanvases } from '@/components/Graphs/utils'
import { useRoute } from 'vue-router'
import { useGraphsQueries } from '@/store/Queries/graphsQueries'

const route = useRoute()
const store = useGraphsQueries()
const instance = computed(() => store.node.ipInterfaces?.[0].ipAddress as string)

const nodeLatency = computed<GraphProps>(() => {
  return {
    label: 'ICMP Response Time',
    metrics: ['response_time_msec'],
    monitor: 'ICMP',
    nodeId: route.params.id as string,
    instance: instance.value,
    timeRange: 10,
    timeRangeUnit: TimeRangeUnit.Minute
  }
})

const bytesIn = computed<GraphProps>(() => {
  return {
    label: 'Bytes Inbound',
    metrics: ['ifInOctets'],
    monitor: 'SNMP',
    nodeId: route.params.id as string,
    instance: instance.value,
    timeRange: 10,
    timeRangeUnit: TimeRangeUnit.Minute
  }
})

const bytesOut = computed<GraphProps>(() => {
  return {
    label: 'Bytes Outbound',
    metrics: ['ifOutOctets'],
    monitor: 'SNMP',
    nodeId: route.params.id as string,
    instance: instance.value,
    timeRange: 10,
    timeRangeUnit: TimeRangeUnit.Minute
  }
})

const bytesInOut = computed<GraphProps>(() => {
  return {
    label: 'Bytes Inbound / Outbound',
    metrics: ['ifInOctets', 'ifOutOctets'],
    monitor: 'SNMP',
    nodeId: route.params.id as string,
    instance: instance.value,
    timeRange: 10,
    timeRangeUnit: TimeRangeUnit.Minute
  }
})

const hcIn = computed<GraphProps>(() => {
  return {
    label: 'ifHCInOctets',
    metrics: ['ifHCInOctets'],
    monitor: 'SNMP',
    nodeId: route.params.id as string,
    instance: instance.value,
    timeRange: 10,
    timeRangeUnit: TimeRangeUnit.Minute
  }
})

const hcOut = computed<GraphProps>(() => {
  return {
    label: 'ifHCOutOctets',
    metrics: ['ifHCOutOctets'],
    monitor: 'SNMP',
    nodeId: route.params.id as string,
    instance: instance.value,
    timeRange: 10,
    timeRangeUnit: TimeRangeUnit.Minute
  }
})

const onDownload = () => {
  const page = document.getElementById('graphs-container') as HTMLElement
  const canvases = document.getElementsByClassName('canvas') as HTMLCollectionOf<HTMLCanvasElement>
  downloadMultipleCanvases(page, canvases)
}

onMounted(async () => {
  store.setNodeId(Number(route.params.id))
  await store.fetchNode()
})
</script>
  
<style scoped lang="scss">
@use "@featherds/styles/themes/variables";

.header-container {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin: var(variables.$spacing-xl) var(variables.$spacing-l);
  
  :deep(.spacing) {
    margin: 0;
  }
}

#graphs-container {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 2rem;
  margin: var(variables.$spacing-xl) var(variables.$spacing-l);
}
</style>
  