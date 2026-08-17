import { Component } from '@angular/core';
import { ConnectionStatusComponent } from './connection-status/connection-status.component';
import { ChatViewComponent } from './chat/chat-view/chat-view.component';
import { DocumentSidebarComponent } from './documents/document-sidebar/document-sidebar.component';

@Component({
  selector: 'app-root',
  imports: [ConnectionStatusComponent, ChatViewComponent, DocumentSidebarComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
