# Chatbot Architecture

FAQ qua WebSocket: `CHATBOT_ASK`, `CHATBOT_GET_FAQ_LIST`. Handler mỏng; tìm kiếm trong `ChatbotProvider` (load `faq_data.json` một lần).  
Không yêu cầu đăng nhập.

**Mục đích:** Hiểu module chatbot tách khỏi auction/payment.  
**Use case:** User hỏi FAQ trên app, tra cứu theo category, relevance scoring theo query.  
**Trong code:** `ChatbotHandler`, `ChatbotProvider`, `PacketRouter`.

```mermaid
flowchart LR
    subgraph Client
        C["AuctionWebSocketClient"]
    end
    subgraph API
        R["PacketRouter"]
        H["ChatbotHandler"]
    end
    subgraph Domain
        P["ChatbotProvider"]
        J["faq_data.json"]
    end

    C -->|CHATBOT_ASK · GET_FAQ_LIST| R --> H --> P
    P -.-> J
    H --> C
```
