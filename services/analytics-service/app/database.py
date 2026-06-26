import os
from datetime import datetime
from decimal import Decimal
from typing import Any

from bson import Decimal128, ObjectId
from motor.motor_asyncio import AsyncIOMotorClient
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker


POSTGRES_URL = os.getenv(
    "POSTGRES_URL",
    "postgresql+psycopg2://freight_user:example@localhost:5433/freight_auction"
)

MONGO_DATABASE = os.getenv("MONGO_DATABASE", "audit_db")

MONGO_URL = os.getenv(
    "MONGO_URL",
    "mongodb://root:example@localhost:27017/?authSource=admin"
)


engine = create_engine(POSTGRES_URL, pool_pre_ping=True)

SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine
)

mongo_client = AsyncIOMotorClient(MONGO_URL)
mongo_db = mongo_client[MONGO_DATABASE]


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def fetch_all(sql: str, params: dict | None = None):
    with engine.connect() as connection:
        result = connection.execute(text(sql), params or {})
        return [dict(row._mapping) for row in result]


def serialize_mongo_value(value: Any):
    if isinstance(value, ObjectId):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, Decimal128):
        return str(value.to_decimal())
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, dict):
        return {key: serialize_mongo_value(item) for key, item in value.items()}
    if isinstance(value, list):
        return [serialize_mongo_value(item) for item in value]
    return value


def serialize_mongo_document(document: dict):
    return {key: serialize_mongo_value(value) for key, value in document.items()}


async def fetch_recent_events(event_types: list[str], limit: int = 10):
    events = await mongo_db["events"].find(
        {"type": {"$in": event_types}}
    ).sort("timestamp", -1).to_list(limit)

    return [serialize_mongo_document(event) for event in events]
