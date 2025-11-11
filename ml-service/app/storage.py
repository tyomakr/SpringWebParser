from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Iterable, List, Optional, Tuple


class Storage:
    def __init__(self, db_path: str) -> None:
        self.db_path = db_path
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(db_path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row

    def init(self) -> None:
        with self._connection:
            self._connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS positives (
                    id INTEGER PRIMARY KEY,
                    url TEXT NOT NULL,
                    hash TEXT NOT NULL,
                    phash TEXT NOT NULL,
                    created_at TEXT,
                    active INTEGER DEFAULT 1
                );

                CREATE UNIQUE INDEX IF NOT EXISTS idx_positives_hash ON positives(hash);

                CREATE TABLE IF NOT EXISTS kv (
                    name TEXT PRIMARY KEY,
                    value TEXT
                );
                """
            )

    def upsert_positive(self, *, hash: str, url: str, phash: str, created_at: str) -> None:
        with self._connection:
            self._connection.execute(
                """
                INSERT INTO positives (hash, url, phash, created_at, active)
                VALUES (?, ?, ?, ?, 1)
                ON CONFLICT(hash) DO UPDATE SET
                    url=excluded.url,
                    phash=excluded.phash,
                    created_at=excluded.created_at,
                    active=1
                """,
                (hash, url, phash, created_at),
            )

    def list_active_positives(self) -> List[sqlite3.Row]:
        cur = self._connection.execute(
            "SELECT id, url, hash, phash FROM positives WHERE active = 1"
        )
        return list(cur.fetchall())

    def set_last_sync(self, value: str) -> None:
        with self._connection:
            self._connection.execute(
                "INSERT INTO kv(name, value) VALUES('last_sync', ?) "
                "ON CONFLICT(name) DO UPDATE SET value=excluded.value",
                (value,),
            )

    def get_last_sync(self) -> Optional[str]:
        cur = self._connection.execute(
            "SELECT value FROM kv WHERE name='last_sync'"
        )
        row = cur.fetchone()
        return row[0] if row else None

    def close(self) -> None:
        self._connection.close()
