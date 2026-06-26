import os

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
