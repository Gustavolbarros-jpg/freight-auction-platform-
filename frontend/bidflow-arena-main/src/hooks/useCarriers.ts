import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";

import { apiFetch } from "@/lib/api";
import { useStore, type Carrier } from "@/lib/store";

interface UserResponse {
  id: string;
  name: string;
  email: string;
  role: "ADMIN" | "TRANSPORTADORA";
  createdAt: string;
}

function mapUserToCarrier(user: UserResponse): Carrier {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    bidsSent: 0,
    winRate: 0,
    status: "ATIVA",
    createdAt: user.createdAt,
  };
}

export function useCarriers() {
  const token = useStore((state) => state.token);
  const setCarriers = useStore((state) => state.setCarriers);

  const query = useQuery({
    queryKey: ["carriers"],
    queryFn: async () => {
      const users = await apiFetch<UserResponse[]>("/v1/auth/users?role=TRANSPORTADORA");
      return users.map(mapUserToCarrier);
    },
    enabled: Boolean(token),
  });

  useEffect(() => {
    if (query.data) {
      setCarriers(query.data);
    }
  }, [query.data, setCarriers]);

  return query;
}
