from fastapi import APIRouter
import pandas as pd
from app.schemas.auctions import AuctionSummaryResponse

from app.database import fetch_all, fetch_recent_events

router = APIRouter()


@router.get("/auctions", response_model=AuctionSummaryResponse)
async def get_auctions_summary():
    by_status_df = pd.DataFrame(fetch_auctions_by_status())

    recent_events = await fetch_recent_events(["AUCTION_OPENED", "AUCTION_CLOSED"])

    return {
        "total": fetch_total_auctions(),
        "by_status": by_status_df.to_dict(orient="records"),
        "closed_with_winner": fetch_closed_auctions_with_winner(),
        "recent_events": recent_events
    }


def fetch_total_auctions():
    result = fetch_all("""
        SELECT COUNT(*) AS total
        FROM auctions
    """)

    return result[0]["total"]


def fetch_auctions_by_status():
    return fetch_all("""
        SELECT status, COUNT(*) AS total
        FROM auctions
        GROUP BY status
        ORDER BY status
    """)


def fetch_closed_auctions_with_winner():
    result = fetch_all("""
        SELECT COUNT(*) AS total
        FROM auctions
        WHERE status = 'CLOSED'
          AND winner_user_id IS NOT NULL
    """)

    return result[0]["total"]