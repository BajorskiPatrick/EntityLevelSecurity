# Entity Level Security (ELS)

A Java library that provides **row-level security** for Spring Boot + JPA/Hibernate applications.
ELS controls which database rows a user can `SELECT`, `INSERT`, `UPDATE`, or `DELETE` —
all enforced transparently via a single `@Secure` annotation on your service methods.

---

## Table of Contents

- [Key Features](#key-features)
- [Architecture](#architecture)
- [How It Works](#how-it-works)
- [Design Patterns](#design-patterns)
- [Integration Guide](#integration-guide)
- [Configuration](#configuration)
- [Permission Model](#permission-model)
- [Demo Application](#demo-application)
- [Project Structure](#project-structure)

---

## Key Features

| Feature | Description |
|---|---|
| **Row-Level Filtering** | `SELECT` queries automatically return only the rows a user is permitted to see (via Hibernate filters) |
| **CRUD Access Control** | `INSERT`, `UPDATE`, and `DELETE` operations are individually guarded per entity per row |
| **Zero-Config `@Secure`** | Parameterless annotation — the library auto-detects the target entity and CRUD action from your code |
| **Bytecode-Based Detection** | Uses ASM to analyze which repository methods your service calls, inferring `Action` and `Entity` automatically |
| **Global Access Strategy** | Choose between **Whitelist** (allow only listed IDs) or **Blacklist** (deny only listed IDs) globally |
| **Role Hierarchy** | Composite Pattern for roles — a `CompositeRole` can contain child roles, permissions are inherited |
| **Automatic Hibernate Filters** | Filter definitions and mappings are injected into all entities at startup — no manual `@Filter` annotations needed |
| **Permission Caching** | Resolved permissions are cached per user/entity/action, invalidated automatically on changes |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Your Application                         │
│                                                                  │
│   Service Layer           Repository Layer        Database       │
│  ┌──────────────┐       ┌──────────────────┐    ┌──────────┐    │
│  │  @Secure      │──────▶│ JpaRepository     │───▶│  Tables   │    │
│  │  myMethod()   │       │ findAll / save    │    │          │    │
│  └──────┬───────┘       └──────────────────┘    └──────────┘    │
│         │                                                        │
│         │ AOP intercept                                          │
│         ▼                                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    ELS Library                           │    │
│  │                                                         │    │
│  │  SecurityAspect ─── PermissionResolver ─── AccessStrategy│    │
│  │       │                    │                     │        │    │
│  │       │              PermissionCache        Whitelist /   │    │
│  │       │                    │               Blacklist     │    │
│  │       │              PermissionRepo                      │    │
│  │       │                    │                              │    │
│  │       ▼                    ▼                              │    │
│  │  Hibernate Filters    els_permissions table               │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

## How It Works

### Request Flow (step by step)

1. **User calls a service method** annotated with `@Secure`

2. **`SecurityAspect` intercepts** the call via Spring AOP (`@Around`)

3. **Auto-detection** — the aspect determines the `Action` and `Entity`:
   - **Primary**: Uses **ASM bytecode analysis** to inspect the method's bytecode, finds calls to Spring Data JPA repository methods (e.g., `findAll`, `save`, `deleteById`), and maps them to Actions (`SELECT`, `INSERT`, `UPDATE`, `DELETE`). The entity is resolved from the repository's generic type parameter (`JpaRepository<Patient, Long>` → `Patient`).
   - **Fallback**: If bytecode analysis fails, falls back to method **name conventions** (`getAllPatients` → SELECT, `addPatient` → INSERT) and **return type / parameter type analysis** (returns `List<Patient>` → entity is Patient).

4. **`PermissionResolver` resolves permissions** for the current user:
   - Checks the `PermissionCache` first
   - If cache miss: queries `els_permissions` table for the user's direct permissions + all inherited role permissions (traversing the Composite Pattern hierarchy)
   - Aggregates all `rowId` values and passes them to the active `AccessStrategy`

5. **`AccessStrategy` generates a `FilterCondition`**:
   - **Whitelist**: `IN (1, 2, 5)` — user can only access these rows
   - **Blacklist**: `NOT IN (3, 7)` — user can access everything except these rows

6. **Action-specific enforcement**:

   | Action | Mechanism |
   |--------|-----------|
   | `SELECT` | Enables Hibernate filter (`elsFilter` or `elsBlacklistFilter`) on the Session — the query automatically returns only permitted rows |
   | `INSERT` | Table-level check: if any INSERT permission exists → allow; otherwise → deny |
   | `UPDATE` / `DELETE` | Row-level check: extracts the target entity ID from the method arguments, calls `strategy.isIdPermitted(id, permittedIds)` |

7. **Result**: The service method either executes normally (with filtered data) or throws `SecurityException`

---

## Design Patterns

ELS uses the following design patterns:

| Pattern | Where | Purpose |
|---------|-------|---------|
| **Strategy** | `WhitelistStrategy`, `BlacklistStrategy` | Encapsulates the access logic (IN vs NOT IN). The active strategy is selected globally via configuration. |
| **Composite** | `Role` → `SimpleRole`, `CompositeRole` | Role hierarchy — a `CompositeRole` contains child roles, permissions are inherited recursively. |
| **Builder** | `Permission`, `User`, `Role`, etc. | Fluent object construction. |
| **Observer** | `PermissionUpdateEvent`, `CacheInvalidator` | When a permission is saved/deleted, an event is published and the cache is automatically invalidated. |
| **Proxy / AOP** | `SecurityAspect` | Transparent interception — security is enforced without modifying business logic. |

---

## Integration Guide

### 1. Add ELS as a dependency

```xml
<dependency>
    <groupId>com.els</groupId>
    <artifactId>els-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure component scanning

In your Spring Boot application class, include the `com.els` package:

```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.your.app", "com.els"})
@EntityScan(basePackages = {"com.your.app", "com.els"})
@EnableJpaRepositories(basePackages = {"com.your.app", "com.els"})
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3. Set the current user

ELS needs to know who the current user is. Implement a servlet filter or interceptor that sets the user in `SecurityContext`:

```java
@Component
public class UserContextFilter implements Filter {

    private final SecurityContext securityContext;
    private final UserRepository userRepository;

    // constructor injection...

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Extract user identity from your auth mechanism (JWT, session, header, etc.)
        String username = extractUsername(httpRequest);

        if (username != null) {
            userRepository.findByUsername(username).ifPresent(securityContext::setCurrentUser);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            securityContext.clear();
        }
    }
}
```

### 4. Annotate your service methods

Simply add `@Secure` to any method that should be guarded. **No parameters needed** — the library auto-detects the entity and action:

```java
@Service
public class PatientService {

    private final PatientRepository patientRepository;

    // ASM detects: findAll → SELECT, entity = Patient (from JpaRepository<Patient, Long>)
    @Secure
    @Transactional(readOnly = true)
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    // ASM detects: save → INSERT (method name starts with "add"), entity = Patient
    @Secure
    @Transactional
    public Patient addPatient(Patient patient) {
        return patientRepository.save(patient);
    }

    // ASM detects: save → UPDATE (method name starts with "update"), entity = Patient
    @Secure
    @Transactional
    public Patient updatePatient(Patient patient) {
        return patientRepository.save(patient);
    }

    // ASM detects: deleteById → DELETE, entity = Patient
    @Secure
    @Transactional
    public void deletePatient(Long patientId) {
        patientRepository.deleteById(patientId);
    }
}
```

> **Method naming matters for `save()`**: Since JPA's `save()` is used for both inserts and updates, the library uses the _service method name_ as a tiebreaker: `add*`/`create*` → INSERT, `update*`/`modify*` → UPDATE.

### 5. Create permissions

Use `PermissionManager` to grant permissions:

```java
// Grant user "dr_house" SELECT access to Patient with id=1
Permission p = Permission.builder()
    .user(drHouse)
    .entity("Patient")
    .action(Action.SELECT)
    .rowId(1L)
    .build();
permissionManager.savePermission(p);

// Grant role "DOCTOR" INSERT permission for Patient (no rowId for INSERT)
Permission p2 = Permission.builder()
    .role(doctorRole)
    .entity("Patient")
    .action(Action.INSERT)
    .rowId(null)
    .build();
permissionManager.savePermission(p2);
```

### 6. That's it!

No `@Filter`, no `@FilterDef`, no `package-info.java` needed in your application.
ELS automatically:
- Registers Hibernate filter definitions (via `@FilterDef` on the `Permission` entity)
- Applies filter mappings to all your JPA entities (via `ElsHibernateIntegrator` SPI)
- Detects which entity and action each `@Secure` method relates to (via ASM bytecode analysis)
- Caches resolved permissions and invalidates them on changes

---

## Configuration

Add to your `application.properties`:

```properties
# Access strategy: WHITELIST (default) or BLACKLIST
els.access-type=WHITELIST
```

| Property | Values | Default | Description |
|----------|--------|---------|-------------|
| `els.access-type` | `WHITELIST`, `BLACKLIST` | `WHITELIST` | **WHITELIST**: user can only access rows explicitly granted. **BLACKLIST**: user can access all rows except those explicitly denied. |

---

## Permission Model

Each permission is stored as a single row in the `els_permissions` table:

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` | Primary key |
| `user_id` | `BIGINT` (FK) | User this permission applies to (nullable — use either user or role) |
| `role_id` | `BIGINT` (FK) | Role this permission applies to (nullable) |
| `entity_name` | `VARCHAR` | Target entity simple name, e.g. `"Patient"` |
| `action` | `VARCHAR` | One of: `SELECT`, `INSERT`, `UPDATE`, `DELETE` |
| `row_id` | `BIGINT` | Specific row ID this permission applies to (null for `INSERT`, which is table-level) |

**Key rules:**
- Each record maps to **exactly one row ID** — no wildcards, no comma-separated lists
- To grant access to multiple rows, create multiple permission records
- `INSERT` permissions have `row_id = NULL` (it's a table-level allow/deny)
- A permission is linked to either a **user** or a **role**, never both
- Role permissions are inherited — if a user has role `HEAD_DOCTOR` which contains `DOCTOR`, the user gets all `DOCTOR` permissions too

---

## Demo Application

The `els-demo` module is a **MediSec Hospital Management System** that demonstrates all ELS features:

### Running

```bash
# Build everything
mvn clean install -DskipTests

# Start the backend (port 8080)
cd els-demo
mvn spring-boot:run

# Start the frontend (port 5173)
cd els-frontend
npm install
npm run dev
```

### Demo Users

| User | Password | Role | Access |
|------|----------|------|--------|
| `admin` | `admin` | ADMIN | Full access to all entities |
| `dr_house` | `password` | HEAD_DOCTOR (= DOCTOR + NURSE) | Patients 1-3, all medical records |
| `dr_strange` | `password` | DOCTOR | Patients 1-2, all medical records |
| `nurse_joy` | `password` | NURSE | Patients 4-5, medical records (no delete) |

### Features

- **Hospital Dashboard**: View departments, patients, medical records — filtered by ELS permissions
- **Admin Panel**: Manage permissions (grant/revoke per row ID), users, roles, and role hierarchy
- **Live Security**: Log in as different users to see different data based on their permissions

---

## Project Structure

```
ELS/
├── els-library/                    # Core security library
│   └── src/main/java/com/els/
│       ├── annotation/
│       │   └── Secure.java             # @Secure marker annotation
│       ├── aspect/
│       │   └── SecurityAspect.java     # AOP aspect with ASM bytecode analysis
│       ├── cache/
│       │   ├── PermissionCache.java    # ConcurrentHashMap-based cache
│       │   └── PermissionKey.java      # Cache key (user + entity + action)
│       ├── config/
│       │   ├── ElsAutoConfiguration.java  # Auto-configures active strategy
│       │   └── ElsProperties.java         # els.access-type property binding
│       ├── context/
│       │   └── SecurityContext.java    # ThreadLocal<User> holder
│       ├── domain/
│       │   ├── Permission.java         # Permission entity + Hibernate FilterDefs
│       │   ├── User.java               # User entity with roles
│       │   ├── Role.java               # Abstract role (Composite pattern)
│       │   ├── SimpleRole.java         # Leaf role
│       │   ├── CompositeRole.java      # Composite role with children
│       │   ├── Action.java             # Enum: SELECT, INSERT, UPDATE, DELETE
│       │   └── AccessType.java         # Enum: WHITELIST, BLACKLIST
│       ├── event/
│       │   ├── PermissionUpdateEvent.java  # Published on permission changes
│       │   └── CacheInvalidator.java       # Listens & invalidates cache
│       ├── filter/
│       │   └── ElsHibernateIntegrator.java # Auto-applies @Filter to all entities
│       ├── manager/
│       │   └── PermissionManager.java  # Save/delete permissions + publish events
│       ├── repository/
│       │   ├── PermissionRepository.java
│       │   ├── UserRepository.java
│       │   └── RoleRepository.java
│       ├── service/
│       │   └── PermissionResolver.java # Resolves permissions → FilterCondition
│       └── strategies/
│           ├── AccessStrategy.java     # Strategy interface + FilterCondition record
│           ├── WhitelistStrategy.java  # IN operator
│           └── BlacklistStrategy.java  # NOT IN operator
│
├── els-demo/                       # Demo Spring Boot application
│   └── src/main/java/com/els/demo/
│       ├── domain/                     # Patient, MedicalRecord, Department
│       ├── repository/                 # Spring Data repositories
│       ├── service/
│       │   └── HospitalService.java    # @Secure annotated methods
│       ├── web/
│       │   ├── HospitalController.java # REST endpoints
│       │   ├── AdminController.java    # Permission management API
│       │   └── UserContextFilter.java  # Sets SecurityContext from auth
│       ├── dto/                        # PermissionRequest, GroupedPermissionDTO
│       └── loader/
│           └── DataLoader.java         # Seeds demo data on startup
│
├── els-frontend/                   # React frontend
│   └── src/
│       ├── components/
│       │   ├── admin/AdminPanel.jsx    # Permission management UI
│       │   └── hospital/              # Dashboard components
│       └── api/axiosConfig.js          # API client
│
└── pom.xml                         # Parent POM (multi-module)
```

---

## Requirements

- **Java 17+**
- **Spring Boot 3.2+**
- **Hibernate 6.3+** (included via Spring Boot)
- **ASM 9.7** (included as dependency for bytecode analysis)
