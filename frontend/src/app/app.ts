import { Component } from '@angular/core';
import { ConnectionStatusComponent } from './connection-status/connection-status.component';

@Component({
  selector: 'app-root',
  imports: [ConnectionStatusComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
