from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel


class CarrierRankingItem(BaseModel):
    user_id: UUID
    total_bids: int
    min_amount: Decimal | None
    max_amount: Decimal | None
    avg_amount: Decimal | None
    validated_bids: int
    rejected_bids: int


class CarriersSummaryResponse(BaseModel):
    ranking: list[CarrierRankingItem]
