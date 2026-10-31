import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VistaService } from '../../services/vista.service';
import { SeatResponseDto, SeatHoldResponse } from '../../models/booking.model';
import { interval, Subscription } from 'rxjs';
// @ts-ignore
import { Client } from '@stomp/stompjs';
// @ts-ignore
import SockJS from 'sockjs-client';
export interface RowGroup {
  rowLabel: string;
  sectionName: string;
  seats: SeatResponseDto[];
}

@Component({
  selector: 'app-venue-booking',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './venue-booking.component.html',
  styleUrls: ['./venue-booking.component.scss']
})
export class VenueBookingComponent implements OnInit, OnDestroy {
  private readonly vistaService = inject(VistaService);

  eventId = 1;
  userId = 'user_' + Math.random().toString(36).substring(2, 7);

  seats = signal<SeatResponseDto[]>([]);
  selectedSeatIds = signal<number[]>([]);
  holdDetails = signal<SeatHoldResponse | null>(null);

  // VIEW SWITCHER: false = Seat Grid, true = Fresh Checkout Page
  showCheckoutPage = signal<boolean>(false);

  // Timer state
  timeRemaining = signal<string>('');
  private timerSub?: Subscription;
  private stompClient?: Client;

  // Checkout form fields
  customerName = '';
  customerEmail = '';
  customerPhone = '';
  paymentMethod = 'UPI';
  selectedUpiApp = 'GPAY'; // GPAY, PHONEPE, PAYTM
  upiId = '';

  bookingSuccess = signal<boolean>(false);
  errorMessage = signal<string>('');
  bookingReference = signal<string>('');

  selectedSeatsList = computed(() => {
    const ids = this.selectedSeatIds();
    return this.seats().filter(s => ids.includes(s.id));
  });

  basePrice = computed(() => {
    return this.selectedSeatsList().reduce((sum, s) => sum + (s.price || 0), 0);
  });

  // 3. Convenience fee remains unchanged
  convenienceFee = computed(() => {
    return this.selectedSeatIds().length * 40;
  });

  // 5. Final Grand Total

  finalTotal = computed(() => {
    if (this.selectedSeatIds().length === 0) return 0;
    return this.basePrice() + this.convenienceFee() + this.taxAmount();
  });

  rows = computed<RowGroup[]>(() => {
    const allSeats = this.seats();
    if (!allSeats.length) return [];

    const rowMap = new Map<string, SeatResponseDto[]>();
    allSeats.forEach((seat, index) => {
      const fallbackRow = String.fromCharCode(65 + Math.floor(index / 6));
      const row = seat.rowLabel || fallbackRow;
      if (!rowMap.has(row)) {
        rowMap.set(row, []);
      }
      rowMap.get(row)!.push(seat);
    });

    return Array.from(rowMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([rowLabel, seats]) => ({
        rowLabel,
        sectionName: seats[0]?.sectionName || (rowLabel === 'A' ? 'VVIP' : ['B', 'C'].includes(rowLabel) ? 'VIP' : 'REGULAR'),
        seats: seats.sort((a, b) => (a.seatNumber || '').localeCompare(b.seatNumber || ''))
      }));
  });

  ngOnInit(): void {
    this.loadSeats();
    this.connectWebSocket();
  }

  ngOnDestroy(): void {
    this.timerSub?.unsubscribe();
    this.disconnectWebSocket();
  }

  loadSeats(): void {
    this.errorMessage.set('');
    this.vistaService.getSeats(this.eventId).subscribe({
      next: (data) => this.seats.set(data),
      error: () => this.errorMessage.set('Failed to load venue seat matrix.')
    });
  }

  toggleSeat(seat: SeatResponseDto): void {
    const isAvailable = !seat.status || seat.status === 'AVAILABLE';
    if (!isAvailable || this.holdDetails()) return;

    const current = this.selectedSeatIds();
    if (current.includes(seat.id)) {
      this.selectedSeatIds.set(current.filter((id) => id !== seat.id));
    } else {
      this.selectedSeatIds.set([...current, seat.id]);
    }
  }

  removeSeat(seatId: number): void {
    if (this.holdDetails()) return;
    this.selectedSeatIds.set(this.selectedSeatIds().filter(id => id !== seatId));
  }

  getSeatLabel(seat: SeatResponseDto): string {
    if (seat.rowLabel) return `${seat.rowLabel}${seat.seatNumber || ''}`;
    const allSeats = this.seats();
    const index = allSeats.findIndex(s => s.id === seat.id);
    if (index !== -1) {
      const rowChar = String.fromCharCode(65 + Math.floor(index / 6));
      const seatNum = (index % 6) + 1;
      return `${rowChar}${seatNum}`;
    }
    return seat.seatNumber || `S${seat.id}`;
  }

  getSeatTier(seat: SeatResponseDto): string {
    if (seat.sectionName) return seat.sectionName;
    const price = seat.price ?? 0;
    if (price >= 1000) return 'VVIP';
    if (price >= 400) return 'VIP';
    return 'REGULAR';
  }

  // 1. CLICK ON HOLD: Reserves seats, leaves Book Directly available
  holdSelectedSeats(): void {
    if (!this.selectedSeatIds().length) return;
    this.errorMessage.set('');

    const heldIds = [...this.selectedSeatIds()];

    this.vistaService.holdSeats({
      eventId: this.eventId,
      seatIds: heldIds,
      userId: this.userId
    }).subscribe({
      next: (res) => {
        this.holdDetails.set(res);

        // Update local seats to HOLD (turns them orange)
        this.seats.update((currentSeats) =>
          currentSeats.map((seat) => {
            if (heldIds.includes(seat.id)) {
              return { ...seat, status: 'HOLD' };
            }
            return seat;
          })
        );

        const expiryStr = res.expiresAt || res.holdExpiresAt;
        const expiryMs = expiryStr ? new Date(expiryStr).getTime() : Date.now() + 10 * 60 * 1000;
        this.startHoldCountdown(expiryMs);
      },
      error: () => this.errorMessage.set('Could not hold selected seats. They may have just been taken.')
    });
  }

  // 2. CLICK ON BOOK DIRECTLY: Immediately switches to the Checkout Page
  navigateToCheckout(): void {
    if (!this.selectedSeatIds().length) return;
    this.errorMessage.set('');
    this.showCheckoutPage.set(true); // <--- OPENS FRESH PAGE
  }

  // 3. BACK BUTTON FROM CHECKOUT
  backToSeatSelection(): void {
    this.showCheckoutPage.set(false);
  }

  // 4. TIMER COUNTDOWN: Transitions to checkout page when hold time completes
  private startHoldCountdown(expiresAtMs: number): void {
    this.timerSub?.unsubscribe();
    this.timerSub = interval(1000).subscribe(() => {
      const diff = Math.max(0, expiresAtMs - Date.now());
      if (diff <= 0) {
        this.timerSub?.unsubscribe();
        this.timeRemaining.set('00:00');
        // Auto-navigate to fresh checkout page when hold time is over
        if (!this.bookingSuccess()) {
          this.showCheckoutPage.set(true);
        }
      } else {
        const mins = Math.floor(diff / 60000);
        const secs = Math.floor((diff % 60000) / 1000);
        this.timeRemaining.set(`${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`);
      }
    });
  }
  // Inside VenueBookingComponent:

  // Promo code states
  promoInput = '';
  appliedCoupon = signal<{ code: string; discountPercent?: number; flatDiscount?: number } | null>(null);
  couponError = signal<string>('');
  couponSuccess = signal<string>('');

  // Available promo codes list
  private readonly validCoupons: Record<string, { discountPercent?: number; flatDiscount?: number; description: string }> = {
    'VISTA10': { discountPercent: 10, description: '10% off on ticket fare' },
    'FIRST50': { flatDiscount: 50, description: '₹50 flat savings' },
    'EARLYBIRD': { discountPercent: 15, description: '15% early bird discount' }
  };

  // 1. Discount amount computed
  discountAmount = computed(() => {
    const coupon = this.appliedCoupon();
    if (!coupon) return 0;

    const base = this.basePrice();
    if (coupon.discountPercent) {
      return Math.round((base * coupon.discountPercent) / 100);
    }
    if (coupon.flatDiscount) {
      return Math.min(coupon.flatDiscount, base);
    }
    return 0;
  });

  // 2. Base Price after discount
  discountedBasePrice = computed(() => {
    return Math.max(0, this.basePrice() - this.discountAmount());
  });

  

  // 4. GST recalculated on (discounted base price + convenience fee)
  taxAmount = computed(() => {
    const taxable = this.discountedBasePrice() + this.convenienceFee();
    return Math.round(taxable * 0.18);
  });

  

  // Apply promo code method
  applyPromoCode(): void {
    this.couponError.set('');
    this.couponSuccess.set('');

    const code = this.promoInput.trim().toUpperCase();
    if (!code) {
      this.couponError.set('Please enter a coupon code.');
      return;
    }

    const coupon = this.validCoupons[code];
    if (coupon) {
      this.appliedCoupon.set({ code, ...coupon });
      this.couponSuccess.set(`Coupon '${code}' applied! ${coupon.description}`);
      this.promoInput = '';
    } else {
      this.couponError.set('Invalid coupon code. Try VISTA10 or FIRST50.');
    }
  }

  // Remove applied coupon
  removePromoCode(): void {
    this.appliedCoupon.set(null);
    this.couponSuccess.set('');
    this.couponError.set('');
  }

  // 5. FINAL SUBMISSION ON FRESH PAYMENT PAGE
  processPayment(): void {
  if (!this.customerName.trim() || !this.customerEmail.trim()) {
    this.errorMessage.set('Please provide your name and email to proceed.');
    return;
  }
  this.errorMessage.set('');

  // Always extract clean numeric IDs from selectedSeatIds()
  const seatIdsToSend: number[] = Array.from(this.selectedSeatIds()).map(id => Number(id));
  const eventIdToSend = this.eventId || 1;
  const hold = this.holdDetails();

  const payload = {
    eventId: eventIdToSend,
    seatIds: seatIdsToSend,
    holdReference: hold?.holdReference || null,
    customerName: this.customerName,
    customerEmail: this.customerEmail,
    customerPhone: this.customerPhone,
    paymentMethod: this.paymentMethod
  };

  if (hold) {
    this.vistaService.confirmBooking(payload).subscribe({
      next: (res: any) => {
        this.timerSub?.unsubscribe();
        this.bookingReference.set(res?.bookingReference || hold.holdReference || 'VST-' + Math.floor(100000 + Math.random() * 900000));
        this.bookingSuccess.set(true);
      },
      error: (err: any) => {
        console.error('Booking confirmation backend error:', err);
        const backendMsg = err?.error?.error || 'Booking confirmation failed. Please retry.';
        this.errorMessage.set(backendMsg);
      }
    });
  } else {
    this.vistaService.directBook(payload).subscribe({
      next: (res: any) => {
        this.timerSub?.unsubscribe();
        this.bookingReference.set(res?.bookingReference || 'VST-' + Math.floor(100000 + Math.random() * 900000));
        this.bookingSuccess.set(true);
      },
      error: (err: any) => {
        console.error('Direct booking backend error:', err);
        const backendMsg = err?.error?.error || 'Direct booking failed. Please try again.';
        this.errorMessage.set(backendMsg);
      }
    });
  }
  
  }

  printTicket(): void {
    window.print();
  }

  resetBooking(): void {
    this.bookingSuccess.set(false);
    this.showCheckoutPage.set(false);
    this.holdDetails.set(null);
    this.selectedSeatIds.set([]);
    this.loadSeats();
  }

  private connectWebSocket(): void {
    const socket = new SockJS('http://localhost:8081/ws-vista');
    this.stompClient = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        this.stompClient?.subscribe(`/topic/events/${this.eventId}/seats`, (message: any) => {
          if (message.body) {
            const update = JSON.parse(message.body);
            this.handleSeatStatusUpdate(update);
          }
        });
      }
    });
    this.stompClient.activate();
  }

  private handleSeatStatusUpdate(update: { seatIds: number[]; status: 'AVAILABLE' | 'HOLD' | 'BOOKED' }): void {
    this.seats.update((current) =>
      current.map((s) => update.seatIds.includes(s.id) ? { ...s, status: update.status } : s)
    );
  }

  private disconnectWebSocket(): void {
    if (this.stompClient?.active) this.stompClient.deactivate();
  }

  // State for UPI QR Modal
  showUpiQrModal = signal<boolean>(false);
  isVerifyingPayment = signal<boolean>(false);

  // Generate standard NPCI UPI payment deep-link
  upiPaymentUrl = computed(() => {
    const vpa = 'vistaarena@upi'; // Replace with your merchant VPA
    const payeeName = 'Vista Stage and Arena';
    const amount = this.finalTotal();
    const note = 'Seat Reservation ' + (this.bookingReference() || 'VST');

    return `upi://pay?pa=${encodeURIComponent(vpa)}&pn=${encodeURIComponent(payeeName)}&am=${amount}&cu=INR&tn=${encodeURIComponent(note)}`;
  });

  // Generate dynamic QR Code image via standard QR API
  qrCodeImageUrl = computed(() => {
  const vpa = 'divyansh123ya-1@okaxis';
  const payee = 'Vista Stage & Arena';
  const amount = this.finalTotal();
  const upiUri = `upi://pay?pa=${vpa}&pn=${encodeURIComponent(payee)}&am=${amount}&cu=INR&tn=VistaSeats`;
  return `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(upiUri)}`;
});

  // Open QR Modal
  openUpiQrModal(): void {
    if (!this.customerName.trim() || !this.customerEmail.trim()) {
      this.errorMessage.set('Please provide your name and email first.');
      return;
    }
    this.errorMessage.set('');
    this.showUpiQrModal.set(true);
  }

  // Close QR Modal
  closeUpiQrModal(): void {
    this.showUpiQrModal.set(false);
    this.isVerifyingPayment.set(false);
  }

  // Simulate instant payment confirmation after scan
  confirmUpiPayment(): void {
    this.isVerifyingPayment.set(true);

    // Simulated 1.5s gateway verification delay
    setTimeout(() => {
      this.isVerifyingPayment.set(false);
      this.showUpiQrModal.set(false);
      this.processPayment();
    }, 1500);
  }
}