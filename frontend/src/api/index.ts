import axios from 'axios'
import type { Device, TimeSlot, ChangeLog, PeakWindow, PeakCapacityLimit, PageResponse, ApiResponse } from '@/types'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const deviceApi = {
  list(params: { page: number; size: number; terminalArea?: string; deviceType?: string }) {
    return instance.get<ApiResponse<PageResponse<Device>>>('/devices', { params })
  },
  get(id: number) {
    return instance.get<ApiResponse<Device>>(`/devices/${id}`)
  },
  create(data: Omit<Device, 'id' | 'createdAt' | 'updatedAt'>) {
    return instance.post<ApiResponse<Device>>('/devices', data)
  },
  update(id: number, data: Omit<Device, 'id' | 'createdAt' | 'updatedAt'>) {
    return instance.put<ApiResponse<Device>>(`/devices/${id}`, data)
  },
  delete(id: number, force = false) {
    return instance.delete<ApiResponse<{ totalSlots: number; invalidatedSlots: number; ongoingSlots: number; forced: boolean }>>(`/devices/${id}`, {
      params: force ? { force: true } : {},
    })
  }
}

export const timeSlotApi = {
  list(params: { page: number; size: number; deviceId?: number }) {
    return instance.get<ApiResponse<PageResponse<TimeSlot>>>('/time-slots', { params })
  },
  get(id: number) {
    return instance.get<ApiResponse<TimeSlot>>(`/time-slots/${id}`)
  },
  getByDeviceId(deviceId: number) {
    return instance.get<ApiResponse<TimeSlot[]>>(`/time-slots/device/${deviceId}`)
  },
  create(data: Omit<TimeSlot, 'id' | 'createdAt' | 'updatedAt'>) {
    return instance.post<ApiResponse<TimeSlot>>('/time-slots', data)
  },
  update(id: number, data: Omit<TimeSlot, 'id' | 'createdAt' | 'updatedAt'>) {
    return instance.put<ApiResponse<TimeSlot>>(`/time-slots/${id}`, data)
  },
  delete(id: number) {
    return instance.delete<ApiResponse<void>>(`/time-slots/${id}`)
  }
}

export const changeLogApi = {
  list(params: { page: number; size: number; deviceId?: number; startDate?: string; endDate?: string }) {
    return instance.get<ApiResponse<PageResponse<ChangeLog>>>('/change-logs', { params })
  }
}

export const statisticsApi = {
  byTime(params: { startTime: string; endTime: string }) {
    return instance.get<ApiResponse<Device[]>>('/statistics/by-time', { params })
  },
  deviceDetail(deviceId: number) {
    return instance.get<ApiResponse<{ device: Device; timeSlots: TimeSlot[] }>>(`/statistics/device-detail/${deviceId}`)
  }
}

export const categoryApi = {
  getDeviceTypes() {
    return instance.get<ApiResponse<string[]>>('/categories/device-types')
  },
  getTerminalAreas() {
    return instance.get<ApiResponse<string[]>>('/categories/terminal-areas')
  }
}

export const peakCapacityApi = {
  windows() {
    return instance.get<ApiResponse<PeakWindow[]>>('/peak-capacity/windows')
  },
  list() {
    return instance.get<ApiResponse<PeakCapacityLimit[]>>('/peak-capacity')
  },
  create(data: { terminalArea: string; deviceType: string; maxConcurrent: number }) {
    return instance.post<ApiResponse<PeakCapacityLimit>>('/peak-capacity', data)
  },
  update(id: number, data: { terminalArea: string; deviceType: string; maxConcurrent: number }) {
    return instance.put<ApiResponse<PeakCapacityLimit>>(`/peak-capacity/${id}`, data)
  },
  delete(id: number) {
    return instance.delete<ApiResponse<void>>(`/peak-capacity/${id}`)
  }
}