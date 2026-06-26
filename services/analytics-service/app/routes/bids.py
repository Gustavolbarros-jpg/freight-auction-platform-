from fastapi import APIRouter

from app.database import fetch_all

router = APIRouter()


@router.get("/bids")
def get_bids_summary():
    return {
        "total": fetch_total_bids(),
        "by_status": fetch_bids_by_status(),
        "amounts": fetch_bid_amounts_summary()
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