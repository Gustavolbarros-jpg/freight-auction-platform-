import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { useStore } from "@/lib/store";

export interface BidResponse {
  id: string;
  auctionId: string;
  carrierId: string;
  amount: number | string;
  status: "RECEIVED" | "VALIDATED" | "REJECTED" | "WINNING";
  receivedAt: string;
}

export function useAuctionBids(auctionId: string) {
  const token = useStore((state) => state.token);

  return useQuery({
    queryKey: ["auction-bids", auctionId],
    queryFn: () => apiFetch<BidResponse[]>(`/v1/bids/auctions/${auctionId}`),
    enabled: Boolean(token && auctionId),
  });
}
