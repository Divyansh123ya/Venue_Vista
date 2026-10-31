import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { 
  SeatResponseDto, 
  SeatHoldRequest, 
  SeatHoldResponse, 
  BookingRequestDto, 
  BookingResponse 
} from '../models/booking.model';

@Injectable({
  providedIn: 'root'
})
export class VistaService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api';

  getSeats(eventId: number): Observable<SeatResponseDto[]> {
  return this.http.get<SeatResponseDto[]>(`${this.baseUrl}/seats/event/${eventId}`);
}

  holdSeats(payload: SeatHoldRequest): Observable<SeatHoldResponse> {
    return this.http.post<SeatHoldResponse>(`${this.baseUrl}/seats/hold`, payload);
  }

  confirmBooking(payload: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/seats/confirm`, payload);
  }

  directBook(payload: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/seats/book`, payload);
  }
}