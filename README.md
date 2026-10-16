## ⚽ Football Tournament Management System (Spring Boot + JWT + MySQL)

A complete **backend REST API** for managing football tournaments, built using **Spring Boot 3**, **JPA/Hibernate**, **MySQL**, **JWT authentication**, and **Swagger API documentation**.

This system allows **administrators** to create tournaments and manage fixtures, while **teams** can register, add players, join tournaments, and view performance dashboards.

---

## 🚀 Tech Stack

| Technology        | Purpose                 |
| ----------------- | ----------------------- |
| Spring Boot 3     | Backend application     |
| Spring Security   | JWT Authentication      |
| JPA + Hibernate   | ORM + Database Access   |
| MySQL             | Relational Database     |
| Swagger / OpenAPI | API Documentation       |
| Lombok            | Reduce boilerplate code |

---

## ✨ Features

### 👨‍💼 Admin

* 🔐 Secure login via JWT
* 🏆 Create tournaments with registration & match windows
* 🧮 Auto-generate **round-robin fixtures**
* 📅 Schedule matches manually
* 📝 Enter match scores / update results
* 🔔 Notifications support (Pluggable)

### 🏟️ Teams

* ✅ Register a football team
* 👥 Add players to the team
* ✍ Register for a tournament (within registration window)
* 📊 View dashboard: matches, goals, points

### 🌐 Public Endpoints

* 📃 View list of tournaments
* 📅 View tournament fixtures
* 📌 See registered teams

---

## 📂 Project Structure

```
src/main/java/com/mjt/Football_Tournament_Management_System
├─ config/         # Security & OpenAPI config
├─ controller/     # REST APIs (Admin, Team, Public, Auth)
├─ dto/           # Request/Response DTOs
├─ entire/        # Entities (Tournament, Team, Match, etc.)
├─ repo/          # Spring Data JPA Repositories
├─ security/      # JWT Filter & Token Service
├─ service/       # Business Logic
└─ resources/
   ├─ application.properties
   └─ data.sql
```

---

## 🔐 Authentication (JWT)

### 🔑 Login (POST)

```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

You receive a token:

```
Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

✅ Use this in headers:

```
Authorization: Bearer <TOKEN>
```

---

## 📘 API Documentation (Swagger UI)

Once the application is running, open:

🔗 **[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)**

---

## 🛠 Installation & Setup

### 1️⃣ Create MySQL Database

```sql
CREATE DATABASE football_tournament;
CREATE USER 'username'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON football_tournament.* TO 'username'@'%';
FLUSH PRIVILEGES;
```

### 2️⃣ Configure `application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/football_tournament
spring.datasource.username=root
spring.datasource.password=password

app.jwt.secret=your-32-character-secret-key-here
app.jwt.expiration-minutes=180
```

### 3️⃣ Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

---

## 🧪 Sample Endpoints

### 📂 Public APIs (No JWT needed)

| Method | Endpoint                                     | Description                   |
| ------ | -------------------------------------------- | ----------------------------- |
| GET    | `/api/public/tournaments`                    | List all tournaments          |
| GET    | `/api/public/tournaments/{id}/fixtures`      | List fixtures of a tournament |
| GET    | `/api/public/tournaments/{id}/registrations` | List registered teams         |

### 🧑‍🤝‍🧑 Team APIs

| Method | Endpoint                                                   | Description      |
| ------ | ---------------------------------------------------------- | ---------------- |
| POST   | `/api/teams/register`                                      | Register a team  |
| POST   | `/api/teams/{teamId}/players`                              | Add players      |
| POST   | `/api/teams/{teamId}/tournaments/{tournamentId}/register`  | Join tournament  |
| GET    | `/api/teams/{teamId}/tournaments/{tournamentId}/dashboard` | Team performance |

### 🛡 Admin APIs (JWT required)

| Method | Endpoint                                           | Description         |
| ------ | -------------------------------------------------- | ------------------- |
| POST   | `/api/admin/tournaments`                           | Create a tournament |
| POST   | `/api/admin/tournaments/{id}/fixtures/round-robin` | Generate fixtures   |
| PATCH  | `/api/admin/matches/{id}/score`                    | Update match score  |

---

## 🤖 Default Test Users

| Username | Password | Role  |
| -------- | -------- | ----- |
| admin    | admin123 | ADMIN |
| team     | team123  | USER  |

---

## 📌 Future Enhancements

✅ Email/SMS notifications
✅ Tournament leaderboard API
✅ Docker & Kubernetes support
✅ React frontend
