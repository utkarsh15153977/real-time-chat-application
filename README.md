# WhatsApp Clone - Chat Application

A full-stack chat application built with Spring Boot backend and React + Three.js frontend.

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Node.js 16+
- PostgreSQL 13+
- Redis 6+
- Maven 3.6+

### 1. Start Database Services

**PostgreSQL:**
```bash
# Create database
createdb chat_application

# Or using psql
psql -U postgres
CREATE DATABASE chat_application;
```

**Redis:**
```bash
# Windows (if installed)
redis-server

# Or using Docker
docker run -d -p 6379:6379 redis:alpine
```

### 2. Start Backend (Port 9090)

```bash
# Option 1: Use the batch file
start-backend.bat

# Option 2: Manual
cd chat-application-backend/chat-application-backend
./mvnw spring-boot:run
```

**Backend will be available at:** http://localhost:9090

### 3. Start Frontend (Port 3000)

```bash
# Option 1: Use the batch file  
start-frontend.bat

# Option 2: Manual
cd chat-frontend
npm install
npm start
```

**Frontend will be available at:** http://localhost:3000

## 🔧 Troubleshooting

### Backend Issues

**"Connection refused" errors:**
- Check if PostgreSQL is running on port 5432
- Check if Redis is running on port 6379
- Verify database credentials in `application.properties`

**CORS errors:**
- Backend CORS is configured for `http://localhost:3000`
- Check SecurityConfig.java if you're using different ports

**Port 9090 already in use:**
- Change `server.port=9090` in `application.properties`
- Update frontend API URL in `chat-frontend/src/api/api.js`

### Frontend Issues

**"Loading chats..." stuck:**
- Check browser console for API errors
- Verify backend is running on port 9090
- Check network tab for failed requests

**WebSocket connection failed:**
- Ensure backend WebSocket endpoint `/ws` is accessible
- Check if STOMP is properly configured

**Search/New Chat not working:**
- Check if users exist in database
- Verify API endpoints are returning data
- Check browser console for errors

### Database Setup

**PostgreSQL connection issues:**
```sql
-- Check if database exists
\l

-- Create user if needed
CREATE USER postgres WITH PASSWORD 'newpassword';
GRANT ALL PRIVILEGES ON DATABASE chat_application TO postgres;
```

**Redis connection issues:**
```bash
# Test Redis connection
redis-cli ping
# Should return: PONG
```

## 🎯 Features

- **Authentication:** JWT-based login/register
- **Real-time Chat:** WebSocket messaging with STOMP
- **Private & Group Chats:** 1-on-1 and multi-user conversations  
- **Message Status:** Sent/Delivered/Read indicators
- **Reactions:** Emoji reactions on messages
- **Typing Indicators:** See when others are typing
- **User Search:** Find and start chats with other users
- **Status Updates:** WhatsApp-style 24-hour statuses
- **Three.js UI:** Animated background with particles and rings

## 📁 Project Structure

```
├── chat-application-backend/     # Spring Boot API
│   ├── src/main/java/
│   │   ├── controller/          # REST endpoints
│   │   ├── entity/              # JPA entities  
│   │   ├── service/             # Business logic
│   │   ├── config/              # Security, WebSocket, CORS
│   │   └── dto/                 # Data transfer objects
│   └── src/main/resources/
│       └── application.properties
├── chat-frontend/               # React + Three.js UI
│   ├── src/
│   │   ├── components/          # React components
│   │   ├── pages/               # Main pages
│   │   ├── api/                 # API client
│   │   └── websocket/           # WebSocket service
│   └── package.json
├── start-backend.bat            # Quick backend startup
├── start-frontend.bat           # Quick frontend startup
└── README.md
```

## 🔗 API Endpoints

**Authentication:**
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `POST /api/auth/logout/{userId}` - Logout user

**Users:**
- `GET /api/users` - Get all users
- `GET /api/users/search?name=...` - Search users
- `GET /api/users/{id}/contacts` - Get user contacts

**Chat Rooms:**
- `POST /api/chatrooms/private` - Create private chat
- `POST /api/chatrooms/group` - Create group chat
- `GET /api/chatrooms/user/{userId}` - Get user's chats

**Messages:**
- `POST /api/messages` - Send message
- `GET /api/messages/chat/{chatRoomId}` - Get chat messages
- `PUT /api/messages/{messageId}/read` - Mark as read

**WebSocket:**
- `CONNECT /ws` - WebSocket endpoint
- `SEND /app/chat.send` - Send message
- `SEND /app/chat.typing` - Typing indicator
- `SUBSCRIBE /topic/chat/{roomId}` - Receive messages
- `SUBSCRIBE /topic/typing/{roomId}` - Receive typing

## 🎨 UI Features

- **WhatsApp-style Design:** Green color scheme, familiar layout
- **Three.js Background:** Animated particles and wireframe rings
- **Responsive Layout:** Works on desktop and mobile
- **Real-time Updates:** Live message delivery and typing indicators
- **Emoji Reactions:** Click and hold messages to react
- **Date Grouping:** Messages grouped by date with dividers
- **Connection Status:** Shows online/offline/connecting status

## 🐛 Common Issues

1. **Blank screen:** Check browser console for JavaScript errors
2. **API 404 errors:** Verify backend is running and endpoints exist
3. **CORS errors:** Check SecurityConfig.java CORS settings
4. **Database errors:** Verify PostgreSQL connection and credentials
5. **WebSocket errors:** Check if STOMP configuration is correct
6. **Build errors:** Run `npm install` and check for dependency issues

## 📝 Development Notes

- Backend uses JWT for stateless authentication
- Frontend stores JWT in localStorage
- WebSocket uses STOMP protocol over SockJS
- Database uses JPA with automatic schema updates
- Redis is used for caching and session management
- Three.js renders animated background elements