# Notifications Service

Real-time notification microservice for the FoodChain platform. Pushes order events to connected clients via raw WebSocket (used by frontend hooks) and legacy STOMP/SockJS.

## Port

`8087` — all REST endpoints are served under `/api` (e.g. `http://localhost:8087/api/notifications/ws-info`).

WebSocket endpoints do NOT use the `/api` context-path prefix; they are registered directly on the servlet container.

---

## Raw WebSocket Endpoints

These are the endpoints consumed by the frontend hooks (`useKitchenQueue`, `useOrderTracker`, `useManagerOrders`).

| Hook | WebSocket URL | Key |
|---|---|---|
| `useKitchenQueue` | `ws://[host]/ws/kitchen/{branchId}` | `branchId` |
| `useOrderTracker` | `ws://[host]/ws/orders/{orderId}` | `orderId` |
| `useManagerOrders` | `ws://[host]/ws/manager/{branchId}` | `branchId` |

### Message Payload (JSON)

Every message pushed over the raw WebSocket connections uses this shape:

```json
{
  "orderId":    "string",
  "branchId":   "string",
  "customerId": "string",
  "oldStatus":  "string | null",
  "newStatus":  "string",
  "updatedBy":  "string | null",
  "timestamp":  "2024-01-01T12:00:00Z"
}
```

### Broadcast rules

| Kafka topic | Kitchen | Order Tracker | Manager |
|---|---|---|---|
| `order.received` | branchId | — | branchId |
| `order.status.updated` | branchId | orderId | branchId |

---

## STOMP / SockJS (Legacy)

Connect to `/ws-notifications` using SockJS and subscribe to `/topic/customer/{customerId}` to receive `CustomerNotification` objects:

```json
{
  "type":      "ORDER_RECEIVED | STATUS_UPDATE | ORDER_READY",
  "orderId":   "string",
  "title":     "string",
  "message":   "string",
  "status":    "string",
  "timestamp": "2024-01-01T12:00:00"
}
```

---

## Kafka Topics Consumed

| Topic | Group ID | Description |
|---|---|---|
| `order.received` | `notifications-service-group` | New order placed |
| `order.status.updated` | `notifications-service-group` | Order status changed by kitchen/staff |
| `order.ready` | `notifications-service-group` | Order ready for pickup/delivery |

---

## Connecting from a Browser

### Raw WebSocket

```js
// Kitchen queue — listen for all orders in a branch
const ws = new WebSocket('ws://localhost:8087/ws/kitchen/branch-001');

ws.onmessage = (event) => {
  const payload = JSON.parse(event.data);
  console.log('New order event:', payload);
};

ws.onclose = () => console.log('WebSocket closed');
```

```js
// Order tracker — watch a single order
const ws = new WebSocket('ws://localhost:8087/ws/orders/order-abc123');

ws.onmessage = (event) => {
  const { newStatus, timestamp } = JSON.parse(event.data);
  console.log(`Status updated to ${newStatus} at ${timestamp}`);
};
```

```js
// Manager dashboard — branch-level order stream
const ws = new WebSocket('ws://localhost:8087/ws/manager/branch-001');

ws.onmessage = (event) => {
  const payload = JSON.parse(event.data);
  console.log('Manager event:', payload);
};
```

### STOMP / SockJS (Legacy)

```js
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8087/ws-notifications'),
  onConnect: () => {
    client.subscribe(`/topic/customer/${customerId}`, (msg) => {
      const notification = JSON.parse(msg.body);
      console.log(notification);
    });
  },
});

client.activate();
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/notifications_db` | MySQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | MySQL username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | MySQL password |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | `http://localhost:8761/eureka/` | Eureka server URL |
| `SPRING_CONFIG_IMPORT` | `optional:configserver:http://localhost:8888` | Config server URL |
