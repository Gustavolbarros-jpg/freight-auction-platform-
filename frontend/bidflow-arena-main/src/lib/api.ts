import { useStore } from "./store";
import { API_BASE_URL } from "./config";

type ApiFetchOptions = RequestInit & {
  auth?: boolean;
};

async function readErrorMessage(response: Response) {
  const fallback = `Erro ${response.status} ao chamar a API`;

  try {
    const data = await response.json();
    return data.message ?? data.error ?? fallback;
  } catch {
    return fallback;
  }
}

export async function apiFetch<T>(
  path: string,
  { auth = true, headers, ...options }: ApiFetchOptions = {},
): Promise<T> {
  const token = useStore.getState().token;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(auth && token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}
