import { defineStore } from 'pinia'
import { FeatherSortObject } from '@/types'
import { Node, Alarm, Coordinates, QueryParameters, AlarmModificationQueryVariable } from '@/types/map'
import { LatLngBounds } from 'leaflet'
import { SORT } from '@featherds/table'
import { numericSeverityLevel } from '@/components/Map/utils'
import { useQuery } from 'villus'
import { ListDevicesForGeomapDocument } from '@/types/graphql'


export interface State {
  fetchedNodes: Node[]
  nodesWithCoordinates: Node[]
  alarms: Alarm[]
  interestedNodesID: string[]
  mapCenter: Coordinates
  mapBounds: LatLngBounds | undefined
  selectedSeverity: string
  searchedNodeLabels: string[]
  nodeSortObject: FeatherSortObject
  alarmSortObject: FeatherSortObject
}

const mock = [
  {
    'id': 1,
    'label': 'Ottawa',
    'foreignSource': 'Foreign Source',
    'foreignId': 'ID',
    'labelSource': 'Label Source',
    'sysOid': 'SysO ID',
    'sysName': 'Sys Name',
    'sysDescription': 'Sys Description',
    'sysContact': 'Sys Contact',
    'sysLocation': 'Sys Location',
    'location': {
      'latitude': 45.41767120361328,
      'longitude': -75.6946105957031
    }
  },
  {
    'id': 2,
    'label': 'California',
    'foreignSource': 'Foreign Source',
    'foreignId': 'ID',
    'labelSource': 'Label Source',
    'sysOid': 'SysO ID',
    'sysName': 'Sys Name',
    'sysDescription': 'Sys Description',
    'sysContact': 'Sys Contact',
    'sysLocation': 'Sys Location',
    'location': {
      'latitude': 36.53391143881437,
      'longitude': -119.7084971887121
    }
  },
  {
    'id': 3,
    'label': 'France',
    'foreignSource': 'Foreign Source',
    'foreignId': 'ID',
    'labelSource': 'Label Source',
    'sysOid': 'SysO ID',
    'sysName': 'Sys Name',
    'sysDescription': 'Sys Description',
    'sysContact': 'Sys Contact',
    'sysLocation': 'Sys Location',
    'location': {
      'latitude': 46.691974355366646,
      'longitude': 2.3779300253682836
    }
  }
]

export const useMapStore = defineStore('mapStore', {
  state: () => 
    <State>{
      fetchedNodes: [],
      nodesWithCoordinates: [],
      alarms: [],
      interestedNodesID: [],
      mapCenter: { latitude: 37.776603506225115, longitude: -33.43824554266541 },
      mapBounds: undefined,
      selectedSeverity: 'NORMAL',
      searchedNodeLabels: [],
      nodeSortObject: { property: 'label', value: SORT.ASCENDING },
      alarmSortObject: { property: 'id', value: SORT.DESCENDING }
    },
  actions: {
    async fetchNodes() {
      const { data: devices } = await useQuery({
        query: ListDevicesForGeomapDocument
      })

      this.fetchedNodes = this.nodesWithCoordinates = devices?.value?.listDevices?.devices || []
    },
    fetchAlarms (queryParameters?: QueryParameters) {
      const defaultParams = queryParameters || { limit: 5000, offset: 0 }
      // todo: add graphQL query
      const resp = [] as Alarm[]
    
      if (resp) {
        this.alarms = resp
      }

      return this.alarms
    },
    async modifyAlarm(alarmQueryVariable: AlarmModificationQueryVariable) {
      // todo: add graphQL query
      const resp = {}
      return resp
    },
    filterNodesInBounds() {
      this.nodesWithCoordinates = this.fetchedNodes.filter(node => {
        const location = {
          lat: node.location.latitude,
          lng: node.location.longitude
        }
        return this.mapBounds.contains(location)
      })
    }
  },
  getters: {
    getNodeAlarmSeverityMap(state: State): Record<string, string> {
      const map: { [x: string]: string } = {}
    
      state.alarms.forEach((alarm: Alarm) => {
        if (numericSeverityLevel(alarm.severity) > numericSeverityLevel(map[alarm.nodeLabel])) {
          map[alarm.nodeLabel] = alarm.severity.toUpperCase()
        }
      })
    
      return map
    }
  }
})
