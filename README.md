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

### 2️⃣ Configure environment variables

Secrets are read from the environment (never commit them):

| Variable | Required | Default |
| -------- | -------- | ------- |
| `APP_JWT_SECRET` | yes (min. 32 chars) | - |
| `APP_ADMIN_PASSWORD` | to create the first admin on startup | empty (no admin created) |
| `APP_ADMIN_USERNAME` | no | `admin` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | no | local MySQL, `root`/`root` |
| `APP_CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` |

Tests run on an in-memory H2 database (profile `test`), no MySQL needed: `./mvnw test`.

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
| GET    | `/api/public/tournaments/{id}/categories`    | Categories with their zones   |
| GET    | `/api/public/zones/{id}/registrations`       | Teams of a zone               |
| GET    | `/api/public/zones/{id}/fixtures`            | Fixtures of a zone            |
| GET    | `/api/public/zones/{id}/standings`           | Zone table (3/1/0 + presence) |
| GET    | `/api/public/matches/{id}/events`            | Goals and cards of a match    |
| GET    | `/api/public/tournaments/{id}/scorers`       | Top scorers                   |
| GET    | `/api/public/tournaments/{id}/cards`         | Booked players                |
| GET    | `/api/public/teams/{id}/players`             | Team roster                   |

### 🧑‍🤝‍🧑 Team APIs

| Method | Endpoint                                                   | Description      |
| ------ | ---------------------------------------------------------- | ---------------- |
| POST   | `/api/teams/register`                                      | Register a team  |
| POST   | `/api/teams/{teamId}/players`                              | Add players      |
| POST   | `/api/teams/{teamId}/tournaments/{tournamentId}/register`  | Join tournament (admin assigns zone) |
| GET    | `/api/teams/{teamId}/tournaments/{tournamentId}/dashboard` | Team performance |

### 🛡 Admin APIs (JWT required)

| Method | Endpoint                                           | Description         |
| ------ | -------------------------------------------------- | ------------------- |
| POST   | `/api/admin/tournaments`                           | Create a tournament |
| POST   | `/api/admin/tournaments/{id}/categories`           | Create category (+ default zone) |
| PUT/DELETE | `/api/admin/categories/{id}`                   | Rename / delete category |
| POST   | `/api/admin/categories/{id}/zones`                 | Add zone            |
| PUT/DELETE | `/api/admin/zones/{id}`                        | Rename / delete zone |
| PATCH  | `/api/admin/registrations/{id}/zone`               | Assign/move team to a zone |
| POST   | `/api/admin/zones/{id}/fixtures/round-robin`       | Generate zone fixtures |
| POST   | `/api/admin/fixtures/manual`                       | Manual fixtures (by zone) |
| PATCH  | `/api/admin/matches/{id}/score`                    | Result as per-player events; presence (walkover 3-0) |

---

## 🤖 Users

Users are stored in the DB with BCrypt-hashed passwords.

- **ADMIN**: created on startup from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`.
- **CAPTAIN**: self sign-up via `POST /api/auth/register`. A captain owns the teams they create and can only modify those.
- `GET /api/auth/me` returns the current user, role and owned team ids.

---

## 📌 Future Enhancements

✅ Email/SMS notifications
✅ Tournament leaderboard API
✅ Docker & Kubernetes support
✅ React frontend
