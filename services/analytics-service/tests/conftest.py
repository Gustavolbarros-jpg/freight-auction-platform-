import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock
from main import app


@pytest.fixture(scope="module")
def client():
    with TestClient(app) as c:
        yield c


MOCK_AUCTION_FETCH_ALL = [
    {"total": 10},
    {"status": "OPEN", "total": 7},
    {"status": "CLOSED", "total": 3},
    {"total": 3},
]

MOCK_BID_FETCH_ALL = [
    {"total": 50},
    {"status": "VALIDATED", "total": 30},
    {"status": "REJECTED", "total": 20},
    {"min_amount": 100.0, "max_amount": 900.0, "avg_amount": 500.0},
]

MOCK_CARRIER_FETCH_ALL = [
    {
        "user_id": "00000000-0000-0000-0000-000000000001",
        "total_bids": 10,
        "min_amount": 100.0,
        "max_amount": 900.0,
        "avg_amount": 500.0,
        "validated_bids": 7,
        "rejected_bids": 3,
    }
]

def make_auction_fetch(sql, *args, **kwargs):
    if "winner_user_id" in sql:
        return [{"total": 3}]
    if "GROUP BY" in sql:
        return [{"status": "OPEN", "total": 7}, {"status": "CLOSED", "total": 3}]
    return [{"total": 10}]


def make_bid_fetch(sql, *args, **kwargs):
    if "MIN" in sql:
        return [{"min_amount": 100.0, "max_amount": 900.0, "avg_amount": 500.0}]
    if "GROUP BY" in sql:
        return [{"status": "VALIDATED", "total": 30}, {"status": "REJECTED", "total": 20}]
    return [{"total": 50}]


@pytest.fixture(autouse=True)
def mock_db():
    with patch("app.routes.auctions.fetch_all", side_effect=make_auction_fetch), \
         patch("app.routes.auctions.fetch_recent_events", new_callable=AsyncMock, return_value=[]), \
         patch("app.routes.bids.fetch_all", side_effect=make_bid_fetch), \
         patch("app.routes.bids.fetch_recent_events", new_callable=AsyncMock, return_value=[]), \
         patch("app.routes.carriers.fetch_all", return_value=MOCK_CARRIER_FETCH_ALL), \
         patch("app.routes.carriers.fetch_recent_events", new_callable=AsyncMock, return_value=[]):
        yield