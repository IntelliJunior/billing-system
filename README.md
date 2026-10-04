# Billing System (single project)

A full-stack billing / invoicing app — customers, a product catalog, invoices with
line items (auto-calculated tax and totals), and payment tracking. This is a **single
Maven project**: the Spring Boot build compiles the React frontend and embeds it in the
same jar, so one command builds and runs everything on **one port**.

- **Backend:** Spring Boot 3, Spring Data JPA, Bean Validation, MySQL
- **Frontend:** React 18, React Router, Axios — built automatically by Maven
  (via `frontend-maven-plugin`, no separate Node.js install required) and served
  by Spring Boot as static content

## Project Structure

```
billing-system/
├── pom.xml                    # Builds the React app, then packages backend + frontend into one jar
├── frontend/                  # React source
│   └── src/{api,pages}/...
└── src/main/java/com/billing/
    ├── model/                 # JPA entities: Customer, Product, Invoice, InvoiceItem, Payment
    ├── repository/            # Spring Data JPA repositories
    ├── dto/                   # Request DTOs with validation
    ├── service/                # Business logic (totals, tax, payment status)
    ├── controller/             # REST controllers (/api/**)
    ├── config/                 # WebConfig — lets React Router handle page refreshes
    └── exception/               # Centralized error handling
```

## Prerequisites

- **Java 17+** and **Maven** installed
- **MySQL** running locally (or skip it — see step 1 below). You do **not** need Node.js
  installed; Maven downloads its own local copy automatically the first time you build.

## 1. Configure the database

Open `src/main/resources/application.properties`. By default it points at MySQL:

```properties
spring.datasource.username=root
spring.datasource.password=root
```

The `billing_system` database is created automatically on first run — just make sure
MySQL is running and these credentials match your setup.

**Don't have MySQL set up?** Comment out the MySQL block in that file and uncomment the
H2 in-memory block below it. The app then runs with a disposable in-memory database —
zero setup, but data resets every restart.

## 2. Build and run — one command

```bash
mvn clean package
java -jar target/billing-system-1.0.0.jar
```

`mvn clean package` does everything: downloads a local Node/npm, runs `npm install` and
`npm run build` inside `frontend/`, copies the compiled React app into the jar as static
content, and builds the Spring Boot jar. The **first build will take a few minutes**
(downloading Node and npm packages); after that, rebuilds are much faster.

Once running, open **http://localhost:8080** — the React app and the REST API
(`/api/**`) are both served from that same URL. There's no second server or port to run.

### Faster inner-loop while developing

If you're actively editing code and don't want to rebuild the frontend on every change:

```bash
# Terminal 1 — backend only, on port 8080
mvn spring-boot:run

# Terminal 2 — frontend with hot reload, on port 3000
cd frontend
npm install
npm start
```

`frontend/package.json` has `"proxy": "http://localhost:8080"`, so the dev server at
`localhost:3000` automatically forwards `/api/**` calls to the backend. Use
`localhost:3000` while developing, then run `mvn clean package && java -jar ...` to
produce the single deployable jar again.

## REST API Reference

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/customers` | List all customers |
| GET | `/api/customers/{id}` | Get one customer |
| POST | `/api/customers` | Create customer |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer |
| GET | `/api/products` | List all products |
| POST | `/api/products` | Create product |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product |
| GET | `/api/invoices` | List invoices (optional `?customerId=` or `?status=`) |
| GET | `/api/invoices/{id}` | Get invoice with items + payments |
| POST | `/api/invoices` | Create invoice (customer + line items) |
| POST | `/api/invoices/{id}/payments` | Record a payment against an invoice |
| POST | `/api/invoices/{id}/cancel` | Cancel an unpaid invoice |
| DELETE | `/api/invoices/{id}` | Delete an invoice |

### Sample: Create an invoice

```json
POST /api/invoices
{
  "customerId": 1,
  "dueDate": "2026-10-15",
  "items": [
    { "description": "Web design services", "quantity": 10, "unitPrice": 50.00, "taxPercent": 18 },
    { "productId": 3, "quantity": 2 }
  ]
}
```

### Sample: Record a payment

```json
POST /api/invoices/1/payments
{
  "amount": 250.00,
  "method": "UPI",
  "referenceNote": "Paid via GPay"
}
```

## Notes

- Invoice numbers are auto-generated (`INV-YYYYMMDD-XXXXXX`).
- Tax is computed per line item and summed; if a line item is linked to a catalog product,
  price/tax default from the product but can be overridden per line.
- A payment cannot exceed an invoice's remaining balance due.
- An invoice can only be cancelled if no payments have been recorded against it yet.
- If MySQL isn't reachable, the app will fail to start with a connection error — double
  check the DB is running and the credentials in `application.properties` are correct,
  or switch to the H2 config described above.
