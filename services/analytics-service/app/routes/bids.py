from fastapi import APIRouter
import pandas as pd
from app.schemas.bids import BidSummaryResponse

from app.database import fetch_all, fetch_recent_events

router = APIRouter()


@router.get("/bids", response_model=BidSummaryResponse)
async def get_bids_summary():
    by_status_df = pd.DataFrame(fetch_bids_by_status())

    recent_events = await fetch_recent_events(["BID_RECEIVED", "BID_VALIDATED", "BID_REJECTED"])

    return {
        "total": fetch_total_bids(),
        "by_status": by_status_df.to_dict(orient="records"),
        "amounts": fetch_bid_amounts_summary(),
        "recent_events": recent_events
    }


def fetch_total_bids():
    result = fetch_all("""
        SELECT COUNT(*) AS total
        FROM bids
    """)

    return result[0]["total"]


def fetch_bids_by_status():
    return fetch_all("""
        SELECT status, COUNT(*) AS total
        FROM bids
        GROUP BY status
        ORDER BY status
    """)


def fetch_bid_amounts_summary():
    result = fetch_all("""
        SELECT
            MIN(amount) AS min_amount,
            MAX(amount) AS max_amount,
            AVG(amount) AS avg_amount
        FROM bids
    """)

    return result[0]