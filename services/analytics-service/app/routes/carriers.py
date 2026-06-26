from fastapi import APIRouter

from app.database import fetch_all

router = APIRouter()


@router.get("/carriers")
def get_carriers_summary():
    return {
        "ranking": fetch_carrier_ranking()
    }


def fetch_carrier_ranking():
    return fetch_all("""
        SELECT
            carrier_id,
            COUNT(*) AS total_bids,
            MIN(amount) AS min_amount,
            MAX(amount) AS max_amount,
            AVG(amount) AS avg_amount,
            SUM(CASE WHEN status = 'VALIDATED' THEN 1 ELSE 0 END) AS validated_bids,
            SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_bids
        FROM bids
        GROUP BY carrier_id
        ORDER BY validated_bids DESC, total_bids DESC
    """)