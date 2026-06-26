from fastapi import APIRouter
import pandas as pd

from app.database import fetch_all
from app.schemas.carriers import CarriersSummaryResponse


router = APIRouter()


@router.get("/carriers", response_model=CarriersSummaryResponse)
def get_carriers_summary():
    ranking_df = pd.DataFrame(fetch_carrier_ranking())

    return {
        "ranking": ranking_df.to_dict(orient="records")
    }


def fetch_carrier_ranking():
    return fetch_all("""
        SELECT
            user_id,
            COUNT(*) AS total_bids,
            MIN(amount) AS min_amount,
            MAX(amount) AS max_amount,
            AVG(amount) AS avg_amount,
            SUM(CASE WHEN status = 'VALIDATED' THEN 1 ELSE 0 END) AS validated_bids,
            SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_bids
        FROM bids
        GROUP BY user_id
        ORDER BY validated_bids DESC, total_bids DESC
    """)
