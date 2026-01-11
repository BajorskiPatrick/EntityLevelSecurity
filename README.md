# ELS Java Project

## Requirements
*   Java 21
*   Maven

## Building
```bash
mvn clean install
```

## Running the Demo
```bash
cd els-demo
mvn spring-boot:run
```

## Testing Security (Manual Verification)
The Demo App runs on `http://localhost:8080`.
The `DataLoader` initializes:
1.  **Alice** (Manager) -> Can see Laptops (ID 1) + Everything Employee sees.
2.  **Bob** (Employee) -> Can see Mouse (ID 2), Chair (ID 3).
3.  **Charlie** (No Role) -> Sees nothing.

### Test Scenarios
**1. Bob (Employee)**
```bash
curl -H "X-User: bob" http://localhost:8080/api/products
```
*Expected*: Mouse, Chair.

**2. Alice (Manager)**
```bash
curl -H "X-User: alice" http://localhost:8080/api/products
```
*Expected*: Laptop, Mouse, Chair.

**3. Anonymous/Unauthorized**
```bash
curl http://localhost:8080/api/products
```
*Expected*: 403 or Error (Strict mode).
