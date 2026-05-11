import sqlite3, os
from pathlib import Path

DB_PATH = Path.home() / ".claude" / "nim_sessions.db"
DB_PATH.parent.mkdir(exist_ok=True)

def init_db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        session TEXT NOT NULL,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        model TEXT DEFAULT '',
        ts INTEGER DEFAULT (strftime('%s','now'))
    )""")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_s ON messages(session)")
    conn.commit(); conn.close()

init_db()

def save_session(session_id: str, role: str, content: str, model: str = ""):
    try:
        conn = sqlite3.connect(DB_PATH)
        conn.execute(
            "INSERT INTO messages(session,role,content,model) VALUES(?,?,?,?)",
            (session_id, role, str(content)[:10000], model)
        )
        conn.execute("""DELETE FROM messages WHERE session=? AND id NOT IN
            (SELECT id FROM messages WHERE session=? ORDER BY id DESC LIMIT 120)""",
            (session_id, session_id))
        conn.commit(); conn.close()
    except Exception as e:
        print(f"[Session] Save error: {e}")
