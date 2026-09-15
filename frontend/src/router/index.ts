import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '@/views/Dashboard.vue'
import DeviceList from '@/views/DeviceList.vue'
import DeviceForm from '@/views/DeviceForm.vue'
import TimeSlotList from '@/views/TimeSlotList.vue'
import TimeSlotForm from '@/views/TimeSlotForm.vue'
import ChangeLogList from '@/views/ChangeLogList.vue'
import Statistics from '@/views/Statistics.vue'
import PeakCapacityList from '@/views/PeakCapacityList.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Dashboard },
    { path: '/devices', component: DeviceList },
    { path: '/devices/add', component: DeviceForm },
    { path: '/devices/edit/:id', component: DeviceForm },
    { path: '/time-slots', component: TimeSlotList },
    { path: '/time-slots/allocate', component: TimeSlotForm },
    { path: '/time-slots/edit/:id', component: TimeSlotForm },
    { path: '/peak-capacity', component: PeakCapacityList },
    { path: '/change-logs', component: ChangeLogList },
    { path: '/statistics', component: Statistics },
  ]
})

export default router