# Entity Level Security (ELS) - Java Implementation
## MediSec Hospital System Demo

Reference implementation of a Row-Level Security library for Java Spring Boot applications, demonstrating the **Composite**, **Strategy**, **Flyweight**, **Observer**, and **Builder** Design Patterns.

The project consists of:
*   **`els-library`**: The core security engine.
*   **`els-demo`**: A Hospital Management System ("MediSec") demonstrating the library.
*   **`els-frontend`**: React Dashboard for managing the hospital and configuring security.

---

## 🚀 Getting Started

### Prerequisites
*   Java 21
*   Maven
*   Node.js & npm (for Frontend)

### 1. Build & Run Backend
```bash
# In the root 'EntityLevelSecurity' folder
mvn clean install
cd els-demo
mvn spring-boot:run
```
The backend API starts at `http://localhost:8080`.

### 2. Run Frontend
```bash
cd els-frontend
npm install
npm run dev
```
The dashboard starts at `http://localhost:5173`.

---

## 🏥 MediSec Demo Scenarios

The demo initializes with a set of Users, Roles, and Permissions to simulate a real hospital environment.

### 👩‍⚕️ Dr. Gregory House (User: `dr_house`)
*   **Role**: `HeadOfDept` (Composite Role)
*   **Access**:
    *   **Departments**: Can see "Neurology" & "Emergency". **Cannot** see "Cardiology".
    *   **Patients**: Can see patients in his allowed departments.
*   **Mechanism**: `WhitelistStrategy` on Department IDs.

### 👩‍⚕️ Nurse Joy (User: `nurse_joy`)
*   **Role**: `Nurse`
*   **Access**:
    *   **Patients**: Can see all patients **EXCEPT** VIPs.
    *   **VIP Governor** (ID 3) is hidden from her.
*   **Mechanism**: `BlacklistStrategy` (Deny ID 3).

### 👨‍💼 Administrator (User: `admin_strange`)
*   **Role**: Super Admin
*   **Access**: Full visibility of all Records, Patients, and Config.

---

## 🛠️ Architecture & Patters

### Domain Entities
*   `Department`: Hospital wards (filtered by ID).
*   `Patient`: Linked to departments, contains sensitive info (SSN).
*   `MedicalRecord`: Clinical notes and diagnoses.
*   **Security**: All entities are protected by Hibernate `@Filter` definitions injected by the library.

### Design Patterns Implemented
1.  **Composite**: Roles can inherit other roles (e.g., `HeadOfDept` includes `Doctor`).
2.  **Strategy**: `WhitelistStrategy` ('IN' clause) and `BlacklistStrategy` ('NOT IN' clause).
3.  **Flyweight**: `PermissionCache` stores resolved permissions to minimize DB hits.
4.  **Observer**: `CacheInvalidator` listens for permission updates and clears the cache automatically.
5.  **Builder**: Used for constructing `Permission` objects (e.g., `Permission.builder().user(u).build()`).

---

## 🔧 Configuration Panel (Admin)
Visit the **"Admin / Config"** tab in the Frontend to:
1.  View all active permissions.
2.  Grant new permissions to Users or Roles.
3.  Revoke existing permissions.
4.  Switch active user simulation to test changes instantly.

## 🧪 Testing API (Curl)

**Get Patients as Dr. House (Restricted):**
```bash
curl -H "X-User: dr_house" http://localhost:8080/api/hospital/patients
```

**Get Patients as Admin (Full Access):**
```bash
curl -H "X-User: admin_strange" http://localhost:8080/api/hospital/patients
```
