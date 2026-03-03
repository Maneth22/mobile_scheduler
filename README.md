# HabitFlow (mobile_scheduler)

A minimalist Android habit-tracking app backed by a Python FastAPI service.

## What it does
- Track habits daily from an Android app.
- Toggle completion for each habit with a minimal, clean UI.
- View a month-end summary showing completion performance.

## Project Structure
- `android-app/` — Android client (Jetpack Compose UI + Retrofit API client).
- `backend/` — Python FastAPI backend with SQLite persistence.
- `DESCRIPTION.md` — Product description and minimalist design intent.

## Backend Setup (Python)
```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

API docs will be available at `http://localhost:8000/docs`.

## Android Notes
- Backend base URL is currently set to `http://10.0.2.2:8000/` for Android emulator use.
- The UI is intentionally minimalist:
  - one input to add habits,
  - one toggle to mark completion,
  - one summary block for month-end insights.

## Core API Endpoints
- `POST /habits` — create a habit.
- `GET /habits` — list habits.
- `POST /entries` — create/update a habit completion for a date.
- `GET /summary/{YYYY-MM}` — fetch monthly summary.

## Example Workflow
1. Start backend.
2. Open Android app.
3. Add habits (e.g., Walk 30 mins, Read 20 mins).
4. Mark them complete daily.
5. Review summary at end of month.

## Future Improvements
- Proper calendar picker for dates/month selection.
- Local caching/offline sync.
- Per-habit monthly trend charts.
