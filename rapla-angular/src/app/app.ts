import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppToolbarComponent } from './shell/app-toolbar.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AppToolbarComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
