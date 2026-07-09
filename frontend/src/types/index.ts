export interface Device {
  id: number
  deviceCode: string
  deviceType: string
  terminalArea: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface TimeSlot {
  id: number
  deviceId: number
  startTime: string
  endTime: string
  startDate: string
  endDate: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ChangeLog {
  id: number
  deviceId: number
  timeSlotId: number | null
  changeType: string
  beforeValue: string | null
  afterValue: string | null
  operator: string
  changeTime: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}