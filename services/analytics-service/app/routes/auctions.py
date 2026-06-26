from fastapi import APIRouter

from app.database import fetch_all

router = APIRouter()


@router.get("/auctions")
def get_auctions_summary():
    return {
        "total": fetch_total_auctions(),
        "by_status": fetch_auctions_by_status(),
        "closed_with_winner": fetch_closed_auctions_with_winner()
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