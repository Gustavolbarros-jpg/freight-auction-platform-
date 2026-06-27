from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from app.routes import auctions, bids, carriers

app = FastAPI(
    title="Freight Auction Analytics Service",
    version="1.0.0",
    description="Analytics service for freight auction platform",
)

Instrumentator().instrument(app).expose(app)


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "analytics-service"
    }


app.include_router(auctions.router, prefix="/v1/analytics", tags=["auctions"])
app.include_router(bids.router, prefix="/v1/analytics", tags=["bids"])
app.include_router(carriers.router, prefix="/v1/analytics", tags=["carriers"])