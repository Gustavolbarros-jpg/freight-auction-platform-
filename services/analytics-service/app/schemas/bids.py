from decimal import Decimal

from pydantic import BaseModel


class BidStatusCount(BaseModel):
    status: str
    total: int


class BidAmountsSummary(BaseModel):
    min_amount: Decimal | None
    max_amount: Decimal | None
    avg_amount: Decimal | None


class BidSummaryResponse(BaseModel):
    total: int
    by_status: list[BidStatusCount]
    amounts: BidAmountsSummary
