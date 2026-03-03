from datetime import date
import sqlite3
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

DB_PATH = "backend/habits.db"

app = FastAPI(title="Habit Tracker API", version="1.0.0")


class HabitCreate(BaseModel):
    name: str = Field(min_length=1, max_length=80)


class HabitEntryCreate(BaseModel):
    habit_id: int
    completed_on: date
    completed: bool = True


class Habit(BaseModel):
    id: int
    name: str


class HabitEntry(BaseModel):
    id: int
    habit_id: int
    completed_on: date
    completed: bool


class MonthlySummary(BaseModel):
    month: str
    total_habits: int
    total_days_tracked: int
    completion_rate_percent: float


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    conn = get_connection()
    cur = conn.cursor()
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS habits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        )
        """
    )
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS habit_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            habit_id INTEGER NOT NULL,
            completed_on TEXT NOT NULL,
            completed INTEGER NOT NULL,
            UNIQUE(habit_id, completed_on),
            FOREIGN KEY (habit_id) REFERENCES habits(id)
        )
        """
    )
    conn.commit()
    conn.close()


@app.on_event("startup")
def startup_event() -> None:
    init_db()


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/habits", response_model=Habit)
def create_habit(payload: HabitCreate) -> Habit:
    conn = get_connection()
    cur = conn.cursor()
    try:
        cur.execute("INSERT INTO habits(name) VALUES (?)", (payload.name.strip(),))
        conn.commit()
        habit_id = cur.lastrowid
    except sqlite3.IntegrityError as exc:
        raise HTTPException(status_code=409, detail="Habit already exists") from exc
    finally:
        conn.close()

    return Habit(id=habit_id, name=payload.name.strip())


@app.get("/habits", response_model=List[Habit])
def list_habits() -> List[Habit]:
    conn = get_connection()
    cur = conn.cursor()
    rows = cur.execute("SELECT id, name FROM habits ORDER BY id DESC").fetchall()
    conn.close()
    return [Habit(**dict(row)) for row in rows]


@app.post("/entries", response_model=HabitEntry)
def upsert_habit_entry(payload: HabitEntryCreate) -> HabitEntry:
    conn = get_connection()
    cur = conn.cursor()

    habit_row = cur.execute("SELECT id FROM habits WHERE id = ?", (payload.habit_id,)).fetchone()
    if not habit_row:
        conn.close()
        raise HTTPException(status_code=404, detail="Habit not found")

    completed_value = 1 if payload.completed else 0
    cur.execute(
        """
        INSERT INTO habit_entries(habit_id, completed_on, completed)
        VALUES (?, ?, ?)
        ON CONFLICT(habit_id, completed_on)
        DO UPDATE SET completed = excluded.completed
        """,
        (payload.habit_id, payload.completed_on.isoformat(), completed_value),
    )
    conn.commit()
    row = cur.execute(
        "SELECT id, habit_id, completed_on, completed FROM habit_entries WHERE habit_id = ? AND completed_on = ?",
        (payload.habit_id, payload.completed_on.isoformat()),
    ).fetchone()
    conn.close()

    return HabitEntry(
        id=row["id"],
        habit_id=row["habit_id"],
        completed_on=date.fromisoformat(row["completed_on"]),
        completed=bool(row["completed"]),
    )


@app.get("/summary/{month}", response_model=MonthlySummary)
def get_monthly_summary(month: str, habit_id: Optional[int] = None) -> MonthlySummary:
    """month format: YYYY-MM"""
    if len(month) != 7 or month[4] != "-":
        raise HTTPException(status_code=400, detail="month must be in YYYY-MM format")

    conn = get_connection()
    cur = conn.cursor()

    habits_query = "SELECT COUNT(*) AS count FROM habits"
    habits_args: tuple = ()

    entries_query = (
        "SELECT COUNT(*) AS total, SUM(completed) AS completed_total FROM habit_entries "
        "WHERE substr(completed_on, 1, 7) = ?"
    )
    entries_args: tuple = (month,)

    if habit_id is not None:
        habits_query = "SELECT COUNT(*) AS count FROM habits WHERE id = ?"
        habits_args = (habit_id,)
        entries_query += " AND habit_id = ?"
        entries_args += (habit_id,)

    total_habits = cur.execute(habits_query, habits_args).fetchone()["count"]
    summary_row = cur.execute(entries_query, entries_args).fetchone()
    total_days_tracked = summary_row["total"] or 0
    completed_total = summary_row["completed_total"] or 0
    completion_rate = (completed_total / total_days_tracked * 100) if total_days_tracked else 0.0
    conn.close()

    return MonthlySummary(
        month=month,
        total_habits=total_habits,
        total_days_tracked=total_days_tracked,
        completion_rate_percent=round(completion_rate, 2),
    )
