export type SeatStatus = 'AVAILABLE' | 'HOLD' | 'BOOKED';

export interface SeatResponseDto {
  id: number;
  eventId?: number;
  seatNumber: string;
  rowLabel?: string;
  row?: string;
  sectionName?: string;
  tier?: string;
  price?: number;
  status: SeatStatus;
}

export interface SeatHoldRequest {
  eventId: number;
  seatIds: number[];
  userId?: string;
}

export interface SeatHoldResponse {
  holdReference?: string;
  expiresAt?: string;
  holdExpiresAt?: string;
  seatIds?: number[];
  seatId?: number;
}

export interface BookingRequestDto {
  eventId: number;
  seatIds: number[];
  holdReference?: string;
  customerName?: string;
  customerEmail?: string;
  paymentMethod?: string;
}

export interface BookingResponse {
  bookingId?: string;
  id?: number;
  totalAmount?: number;
  status?: string;
}