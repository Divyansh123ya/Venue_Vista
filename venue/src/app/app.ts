import { Component } from '@angular/core';
import { VenueBookingComponent } from './components/venue-booking/venue-booking.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [VenueBookingComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  title = 'venue';
}