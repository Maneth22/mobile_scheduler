# HabitFlow - Minimal Daily Habit Tracker

HabitFlow is an Android app with a Python backend designed for simple daily habit tracking and a clear month-end summary.

## Application Use
- Add personal habits in a lightweight, minimalist interface.
- Mark each habit complete for the day using a single toggle.
- View a monthly summary showing:
  - total habits,
  - total tracked entries,
  - completion rate percentage.

## Design Style
The app intentionally uses minimalist views:
- clean typography,
- compact card layout,
- low-friction actions (add + toggle),
- summary-first reporting.

## Backend Overview
A FastAPI service stores habits and daily completion entries in SQLite and provides summary endpoints consumed by the Android app.
