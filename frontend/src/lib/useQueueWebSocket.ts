import { useEffect, useRef, useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "../context/AuthContext";
import { getCurrentToken } from "./apiClient";
import type { QueueEvent } from "../types/queue";

interface UseQueueWebSocketOptions {
  queueId?: string;
  organizationId?: string;
  onEvent?: (event: QueueEvent) => void;
}

export function useQueueWebSocket({ queueId, organizationId, onEvent }: UseQueueWebSocketOptions) {
  const queryClient = useQueryClient();
  const { accessToken } = useAuth();
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const isSubscribedRef = useRef(false);

  const handleMessage = useCallback(
    (event: QueueEvent) => {
      // Invalidate relevant React Query caches
      if (event.queueId) {
        queryClient.invalidateQueries({ queryKey: ["queue", event.queueId] });
        queryClient.invalidateQueries({ queryKey: ["queueTokens", event.queueId] });
        queryClient.invalidateQueries({ queryKey: ["queues"] });
        queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
        queryClient.invalidateQueries({ queryKey: ["queuesByBranch"] });
      }
      if (event.tokenId) {
        queryClient.invalidateQueries({ queryKey: ["token", event.tokenId] });
        queryClient.invalidateQueries({ queryKey: ["estimate", event.queueId, event.tokenId] });
        queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
      }

      onEvent?.(event);
    },
    [queryClient, onEvent]
  );

  const connect = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN || wsRef.current?.readyState === WebSocket.CONNECTING) {
      return;
    }

    const token = accessToken || getCurrentToken();
    let wsUrl: string;
    const envWs = import.meta.env.VITE_WS_URL;
    const envApi = import.meta.env.VITE_API_BASE_URL;

    if (envWs) {
      wsUrl = envWs;
    } else if (envApi && (envApi.startsWith("http://") || envApi.startsWith("https://"))) {
      const wsProtocol = envApi.startsWith("https://") ? "wss:" : "ws:";
      const hostOnly = envApi.replace(/^https?:\/\//, "").replace(/\/$/, "");
      wsUrl = `${wsProtocol}//${hostOnly}/ws`;
    } else {
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      const host = window.location.host;
      wsUrl = `${protocol}//${host}/ws`;
    }

    try {
      const socket = new WebSocket(wsUrl);
      wsRef.current = socket;

      socket.onopen = () => {
        // Send STOMP CONNECT frame
        const connectHeaders = [
          "CONNECT",
          "accept-version:1.2,1.1,1.0",
          "heart-beat:10000,10000",
          ...(token ? [`Authorization:Bearer ${token}`] : []),
          "\n",
        ].join("\n");
        socket.send(connectHeaders + "\0");
      };

      socket.onmessage = (msgEvent) => {
        const data = msgEvent.data as string;
        if (data.startsWith("CONNECTED")) {
          isSubscribedRef.current = true;

          // Subscribe to queue topic if queueId provided
          if (queueId) {
            const subFrame = [
              "SUBSCRIBE",
              `id:sub-queue-${queueId}`,
              `destination:/topic/queues/${queueId}`,
              "\n",
            ].join("\n");
            socket.send(subFrame + "\0");
          }

          // Invalidate queries on reconnect to restore authoritative DB state
          queryClient.invalidateQueries({ queryKey: ["queues"] });
          queryClient.invalidateQueries({ queryKey: ["customerActiveTokens"] });
          queryClient.invalidateQueries({ queryKey: ["queuesByBranch"] });
          if (queueId) {
            queryClient.invalidateQueries({ queryKey: ["queue", queueId] });
            queryClient.invalidateQueries({ queryKey: ["queueTokens", queueId] });
          }
        } else if (data.startsWith("MESSAGE")) {
          // Extract STOMP message body
          const bodyIndex = data.indexOf("\n\n");
          if (bodyIndex !== -1) {
            const body = data.substring(bodyIndex + 2).replace(/\0$/, "");
            try {
              const queueEvent = JSON.parse(body) as QueueEvent;
              handleMessage(queueEvent);
            } catch {
              // Ignore non-JSON frame
            }
          }
        }
      };

      socket.onclose = () => {
        isSubscribedRef.current = false;
        // Reconnect after 3 seconds
        if (!reconnectTimeoutRef.current) {
          reconnectTimeoutRef.current = window.setTimeout(() => {
            reconnectTimeoutRef.current = null;
            connect();
          }, 3000);
        }
      };

      socket.onerror = () => {
        socket.close();
      };
    } catch {
      // Reconnect on connection setup failure
      if (!reconnectTimeoutRef.current) {
        reconnectTimeoutRef.current = window.setTimeout(() => {
          reconnectTimeoutRef.current = null;
          connect();
        }, 3000);
      }
    }
  }, [accessToken, queueId, organizationId, handleMessage, queryClient]);

  useEffect(() => {
    connect();

    return () => {
      if (reconnectTimeoutRef.current) {
        window.clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [connect]);
}
