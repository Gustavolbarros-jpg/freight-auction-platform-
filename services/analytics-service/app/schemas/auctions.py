from typing import Any

from pydantic import BaseModel


class AuctionStatusCount(BaseModel):
    status: str
    total: int


class AuctionSummaryResponse(BaseModel):
    total: int
    by_status: list[AuctionStatusCount]
    closed_with_winner: int
    recent_events: list[dict[str, Any]]
