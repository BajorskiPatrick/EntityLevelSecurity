# Entity Level Security (ELS)

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 🎯 Cel Projektu

**Entity Level Security (ELS)** to biblioteka Spring Boot Starter implementująca **Row-Level Security (RLS)** na poziomie aplikacji. Biblioteka automatycznie i transparentnie filtruje zapytania SQL, zapewniając że użytkownicy mają dostęp tylko do rekordów, do których posiadają uprawnienia.

### Kluczowe Cechy

- ✅ **Transparentność** - Brak konieczności modyfikacji kodu biznesowego
- ✅ **Deklaratywność** - Pojedyncza adnotacja `@RequiresAcl` aktywuje zabezpieczenia
- ✅ **Kompleksowość** - Obsługa SELECT, UPDATE, DELETE
- ✅ **Uniwersalność** - Działa z PreparedStatement, Statement, JPA, JdbcTemplate
- ✅ **Bezpieczeństwo** - ThreadLocal context, fail-safe mechanizmy
- ✅ **Wydajność** - Minimalne opóźnienie dzięki Proxy Pattern

---

## 🏗️ Architektura

Biblioteka opiera się na trzech głównych warstwach:

```
┌─────────────────────────────────────────────────────────────┐
│  WARSTWA 1: AOP (Aspect-Oriented Programming)               │
│  • Wykrywa metody oznaczone @RequiresAcl                    │
│  • Zarządza cyklem życia kontekstu bezpieczeństwa           │
├─────────────────────────────────────────────────────────────┤
│  WARSTWA 2: ThreadLocal Context                             │
│  • Przechowuje informacje o aktualnym użytkowniku           │
│  • Izolacja per-thread (thread-safe)                        │
├─────────────────────────────────────────────────────────────┤
│  WARSTWA 3: JDBC Proxy Pattern                              │
│  • Przechwytuje zapytania SQL                               │
│  • Modyfikuje SQL (dodaje JOINy/WHERE)                      │
│  • Obsługuje PreparedStatement + Statement                  │
└─────────────────────────────────────────────────────────────┘
```

### Przepływ Danych

```
1. Wywołanie metody z @RequiresAcl
         ↓
2. AclSecurityAspect przechwytuje wywołanie
         ↓
3. Pobiera ID użytkownika z AclUserProvider
         ↓
4. Ustawia kontekst w AclContext (ThreadLocal)
         ↓
5. Wykonuje metodę biznesową
         ↓
6. Metoda wykonuje zapytanie SQL (JPA/JDBC)
         ↓
7. AclConnectionProxy/AclStatementProxy przechwytuje SQL
         ↓
8. SqlRewriter modyfikuje SQL (dodaje ACL filtering)
         ↓
9. Zmodyfikowane SQL trafia do bazy danych
         ↓
10. Zwracane są tylko dozwolone rekordy
         ↓
11. AclSecurityAspect czyści kontekst (finally block)
```

---

## 📦 Komponenty Biblioteki

### 1. **@RequiresAcl** (Annotation)
**Lokalizacja:** `com.els.annotation.RequiresAcl`

**Rola:** Marker annotation - "znacznik" dla metod, które wymagają ACL.

```java
@Retention(RetentionPolicy.RUNTIME)  // Dostępna w runtime (musi być, aby AOP mogła ją wykryć)
@Target(ElementType.METHOD)          // Można użyć tylko na metodach
public @interface RequiresAcl {
}
```

**Zastosowanie:**
```java
@RequiresAcl
public List<Book> getUserBooks() {
    return bookRepository.findAll(); // Automatycznie filtrowane!
}
```

---

### 2. **AclUserProvider** (Interface)
**Lokalizacja:** `com.els.context.AclUserProvider`

**Rola:** **Strategy Pattern** - definiuje kontrakt do pobierania ID aktualnego użytkownika.

```java
@FunctionalInterface
public interface AclUserProvider {
    String getCurrentUserId();
}
```

**Dlaczego interfejs?**
- **Flexibility** - każda aplikacja może mieć swoją implementację (Spring Security, JWT, sesje HTTP)
- **Dependency Inversion** - biblioteka nie jest zależna od konkretnego mechanizmu autoryzacji
- **Testability** - łatwo mockować w testach

**Przykład implementacji w aplikacji klienckiej:**
```java
@Component
public class SecurityUserProvider implements AclUserProvider {
    @Override
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName(); // Pobiera z Spring Security
    }
}
```

**Fallback:** W `ElsAutoConfiguration` jest domyślna implementacja:
```java
@ConditionalOnMissingBean(AclUserProvider.class) // Tylko jeśli aplikacja nie dostarczy własnej
public AclUserProvider defaultUserProvider() {
    return () -> "system-default-user";
}
```

---

### 3. **AclContext** (ThreadLocal Context)
**Lokalizacja:** `com.els.context.AclContext`

**Rola:** Przechowuje stan kontekstu bezpieczeństwa **per-thread** (dla każdego wątku osobno).

```java
public class AclContext {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
    
    public static void setCurrentUser(String userId) {
        currentUser.set(userId);
        active.set(true);
    }
    
    public static String getCurrentUser() { return currentUser.get(); }
    public static boolean isActive() { return active.get(); }
    public static void clear() {
        currentUser.remove();
        active.remove();
    }
}
```

**Dlaczego ThreadLocal?**
- **Thread Safety** - każdy request HTTP ma swój wątek, więc dane nie mieszają się
- **No Leaks** - po zakończeniu requestu `clear()` usuwa dane
- **Performance** - brak synchronizacji, bardzo szybkie

**Cykl życia:**
1. `setCurrentUser("user123")` - Aspekt ustawia na początku
2. `isActive()` - Proxy sprawdza, czy ma filtrować SQL
3. `getCurrentUser()` - Proxy pobiera ID do wstawienia w SQL
4. `clear()` - Aspekt czyści w bloku finally

---

### 4. **AclSecurityAspect** (AOP Interceptor)
**Lokalizacja:** `com.els.aspect.AclSecurityAspect`

**Rola:** **Cross-Cutting Concern** - przechwytuje wywołania metod z `@RequiresAcl`.

```java
@Aspect
public class AclSecurityAspect {
    @Autowired(required = false)
    private AclUserProvider userProvider;

    @Around("@annotation(requiresAcl)")
    public Object manageSecurityContext(ProceedingJoinPoint joinPoint, RequiresAcl requiresAcl) throws Throwable {
        try {
            String userId = (userProvider != null) ? userProvider.getCurrentUserId() : "anonymous";
            logger.log("Activating ACL Security for user: " + userId);
            AclContext.setCurrentUser(userId);
            
            return joinPoint.proceed(); // Wykonuje oryginalną metodę
        } finally {
            logger.log("Deactivating ACL Security");
            AclContext.clear(); // ZAWSZE czyści, nawet przy wyjątku
        }
    }
}
```

**Kluczowe elementy:**

1. **@Around** - Najsilniejszy typ advice, może kontrolować wykonanie metody
2. **ProceedingJoinPoint** - Pozwala wywołać oryginalną metodę (`proceed()`)
3. **try-finally** - Gwarancja, że kontekst zostanie wyczyszczony
4. **@Autowired(required = false)** - Opcjonalna zależność (ma fallback)

**Jak to działa?**
```
User wywołuje → getUserBooks()
                      ↓
Aspekt przechwytuje → setCurrentUser("user123")
                      ↓
Aspekt wywołuje → joinPoint.proceed() (oryginalna metoda)
                      ↓
Metoda wykonuje SQL → (tu działa Proxy!)
                      ↓
Aspekt kończy → clear() w finally
```

---

### 5. **AclDataSourceBeanPostProcessor** (Spring Post Processor)
**Lokalizacja:** `com.els.jdbc.AclDataSourceBeanPostProcessor`

**Rola:** **Interceptor Pattern** na poziomie Spring Context - owija DataSource w proxy.

```java
public class AclDataSourceBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource && !(bean instanceof AclDataSourceProxy)) {
            logger.log("Wrapping DataSource in Proxy: " + beanName);
            return new AclDataSourceProxy((DataSource) bean);
        }
        return bean;
    }
}
```

**Co to robi?**
- Spring tworzy wszystkie beany (w tym DataSource z HikariCP, H2, etc.)
- **Przed** oddaniem ich do aplikacji, ten post-processor je przechwytuje
- Sprawdza, czy bean to `DataSource`
- Jeśli tak, owijamy w `AclDataSourceProxy`
- Aplikacja dostaje proxy zamiast oryginalnego DataSource
- **Transparent** - aplikacja nie wie, że to proxy!

**Warunek:** `!(bean instanceof AclDataSourceProxy)` zapobiega podwójnemu owijaniu.

---

### 6. **AclDataSourceProxy** (DataSource Wrapper)
**Lokalizacja:** `com.els.jdbc.AclDataSourceProxy`

**Rola:** Proxy Pattern - deleguje wszystkie metody do oryginalnego DataSource, **oprócz getConnection()**.

```java
public class AclDataSourceProxy implements DataSource {
    private final DataSource targetDataSource;

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = targetDataSource.getConnection();
        return new AclConnectionProxy(connection); // 🔥 Tu owijamy Connection!
    }
    
    // ... wszystkie inne metody tylko delegują do targetDataSource
}
```

**Dlaczego to kluczowe?**
- Każdy `connection` z bazy jest owijany w `AclConnectionProxy`
- To tam będziemy modyfikować SQL!
- Proxy pattern pozwala na transparentną modyfikację bez zmiany kodu klienta

---

### 7. **AclConnectionProxy** (Connection Wrapper)
**Lokalizacja:** `com.els.jdbc.AclConnectionProxy`

**Rola:** Proxy dla Connection, które przechwytuje zarówno `prepareStatement()` jak i `createStatement()` i modyfikuje SQL.

```java
public class AclConnectionProxy implements Connection {
    private final Connection delegate;
    private final SqlRewriter rewriter = new SqlRewriter();

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (AclContext.isActive()) {
            String newSql = rewriter.addAclToSql(sql, AclContext.getCurrentUser());
            logger.log("Rewritten SQL: " + newSql);
            return delegate.prepareStatement(newSql);
        }
        return delegate.prepareStatement(sql);
    }
    
    @Override
    public Statement createStatement() throws SQLException {
        Statement statement = delegate.createStatement();
        return new AclStatementProxy(statement); // Opakowuje Statement w proxy
    }
    
    // ... wszystkie inne metody delegują do oryginalnego Connection
}
```

**Kluczowe decyzje:**

1. **Warunek `isActive()`**:
    - Jeśli metoda nie ma `@RequiresAcl`, ACL jest nieaktywne → SQL bez zmian
    - Jeśli metoda ma `@RequiresAcl`, ACL jest aktywne → SQL jest modyfikowane

2. **PreparedStatement i Statement**:
    - `prepareStatement()` - SQL modyfikowany podczas tworzenia statement
    - `createStatement()` - zwraca `AclStatementProxy`, który modyfikuje SQL w execute*()

3. **Delegation Pattern**:
    - Wszystkie inne metody (`commit()`, `rollback()`, `close()`) działają normalnie
    - Nie ingerujemy w transakcje ani connection pooling

---

### 8. **AclStatementProxy** (Statement Wrapper)
**Lokalizacja:** `com.els.jdbc.AclStatementProxy`

**Rola:** Proxy dla Statement, które przechwytuje wszystkie metody execute*() i modyfikuje SQL przed wykonaniem.

```java
public class AclStatementProxy implements Statement {
    private final Statement delegate;
    private final SqlRewriter rewriter = new SqlRewriter();

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        if (AclContext.isActive()) {
            String modifiedSql = rewriter.addAclToSql(sql, AclContext.getCurrentUser());
            logger.log("Statement.executeQuery() - Rewritten SQL: " + modifiedSql);
            return delegate.executeQuery(modifiedSql);
        }
        return delegate.executeQuery(sql);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        if (AclContext.isActive()) {
            String modifiedSql = rewriter.addAclToSql(sql, AclContext.getCurrentUser());
            logger.log("Statement.executeUpdate() - Rewritten SQL: " + modifiedSql);
            return delegate.executeUpdate(modifiedSql);
        }
        return delegate.executeUpdate(sql);
    }
    
    // ... execute(), addBatch() i inne metody
}
```

**Dlaczego to ważne?**
- Legacy kod często używa `Statement` zamiast `PreparedStatement`
- Bez obsługi Statement, ACL byłoby możliwe do obejścia
- Zapewnia spójną ochronę niezależnie od typu Statement

---

### 9. **SqlRewriter** (SQL Parser & Transformer)
**Lokalizacja:** `com.els.parser.SqlRewriter`

**Rola:** Parsuje SQL i dodaje ACL filtering dla SELECT, UPDATE i DELETE.

```java
public class SqlRewriter {
    public String addAclToSql(String originalSql, String userId) {
        try {
            Statement statement = CCJSqlParserUtil.parse(originalSql);
            
            if (statement instanceof Select) {
                return handleSelect((Select) statement, userId);
            }
            if (statement instanceof Update) {
                return handleUpdate((Update) statement, userId);
            }
            if (statement instanceof Delete) {
                return handleDelete((Delete) statement, userId);
            }
            
            logger.log("Statement type not handled for ACL: " + statement.getClass().getSimpleName());
        } catch (JSQLParserException e) {
            logger.logException(e);
        }
        return originalSql;
    }
}
```

**Obsługiwane operacje:**

#### SELECT - INNER JOIN do els_acl_table

**Przed:**
```sql
SELECT * FROM books WHERE category = 'fiction'
```

**Po:**
```sql
SELECT * 
FROM books 
INNER JOIN els_acl_table acl_security 
    ON acl_security.row_id = books.id 
    AND acl_security.table_name = 'books'
WHERE acl_security.user_id = 'user123' 
    AND category = 'fiction'
```

#### UPDATE - Subquery w WHERE

**Przed:**
```sql
UPDATE books SET price = 29.99 WHERE category = 'fiction'
```

**Po:**
```sql
UPDATE books SET price = 29.99 
WHERE category = 'fiction' 
AND id IN (
    SELECT row_id FROM els_acl_table 
    WHERE user_id = 'user123' AND table_name = 'books'
)
```

#### DELETE - Subquery w WHERE

**Przed:**
```sql
DELETE FROM books WHERE year < 2000
```

**Po:**
```sql
DELETE FROM books 
WHERE year < 2000 
AND id IN (
    SELECT row_id FROM els_acl_table 
    WHERE user_id = 'user123' AND table_name = 'books'
)
```

**Kluczowe elementy:**

1. **JSQLParser** - biblioteka do parsowania i modyfikacji SQL
2. **INNER JOIN (SELECT)** - filtruje rekordy, do których użytkownik nie ma dostępu
3. **Subquery (UPDATE/DELETE)** - zapobiega modyfikacji/usunięciu cudzych rekordów
4. **table_name** - pozwala używać jednej tabeli ACL dla wielu tabel biznesowych
5. **Bezpieczeństwo** - jeśli parsing się nie uda, zwraca oryginał (fail-safe)

**Obsługiwane typy SQL:**
- ✅ SELECT - pełna obsługa z JOINami
- ✅ UPDATE - filtrowanie przez subquery
- ✅ DELETE - filtrowanie przez subquery
- ⚪ INSERT - bez filtrowania (planowane w przyszłości)
- ⚪ DDL - bez filtrowania

**Ograniczenia:**
- Zakłada kolumnę `id` jako klucz główny
- Nie obsługuje złożonych podzapytań w FROM
- Nie obsługuje UNION/INTERSECT

---

### 10. **ElsAutoConfiguration** (Spring Boot Auto-Configuration)
**Lokalizacja:** `com.els.config.ElsAutoConfiguration`

**Rola:** Automatyczna konfiguracja - tworzy wszystkie beany i inicjalizuje tabelę ACL.

```java
@Configuration
public class ElsAutoConfiguration {
    
    @Bean
    public AclSecurityAspect aclSecurityAspect() {
        return new AclSecurityAspect();
    }
    
    @Bean
    public AclDataSourceBeanPostProcessor aclDataSourceBeanPostProcessor() {
        return new AclDataSourceBeanPostProcessor();
    }
    
    @Bean
    @ConditionalOnMissingBean(AclUserProvider.class)
    public AclUserProvider defaultUserProvider() {
        return () -> "system-default-user";
    }
    
    @Bean
    public AclTableInitializer aclTableInitializer(DataSource dataSource) {
        return new AclTableInitializer(dataSource);
    }
    
    public static class AclTableInitializer {
        private final DataSource dataSource;
        
        @PostConstruct
        public void init() {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String sql = """
                    CREATE TABLE IF NOT EXISTS els_acl_table (
                        id SERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        table_name VARCHAR(50) NOT NULL,
                        row_id BIGINT NOT NULL
                    )
                """;
                stmt.execute(sql);
                logger.log("ACL Table initialized successfully.");
            } catch (Exception e) {
                logger.logException(e);
            }
        }
    }
}
```

**Co się dzieje przy starcie aplikacji?**

1. **Spring Boot** wykrywa `org.springframework.boot.autoconfigure.AutoConfiguration.imports`
2. Ładuje `ElsAutoConfiguration`
3. Tworzy beany:
    - `AclSecurityAspect` - aktywuje AOP
    - `AclDataSourceBeanPostProcessor` - owija DataSource
    - `AclUserProvider` - fallback, jeśli aplikacja nie dostarczy
    - `AclTableInitializer` - tworzy tabelę ACL
4. `@PostConstruct` w `AclTableInitializer` uruchamia `init()`
5. Tabela `els_acl_table` jest tworzona (jeśli nie istnieje)

**Dlaczego `@ConditionalOnMissingBean`?**
- Jeśli aplikacja kliencka dostarczy własny `AclUserProvider`, ten domyślny nie zostanie utworzony
- **Convention over Configuration** - sensowny domyślny, ale można override'ować

---

### 11. **Logger** (Custom Logging)
**Lokalizacja:** `com.els.logger.Logger`

**Rola:** Thread-safe singleton do logowania z automatycznym zamykaniem.

```java
public class Logger {
    private PrintWriter writer;
    private static Logger instance;
    private volatile boolean isClosed = false;
    
    private Logger() {
        try {
            writer = new PrintWriter(new FileWriter("application.log", true));
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }
    
    public static Logger getInstance() {
        if (instance == null) {
            synchronized (Logger.class) {
                if (instance == null) {
                    instance = new Logger();
                }
            }
        }
        return instance;
    }
    
    public void log(String message) {
        String fullMessage = "[" + LocalDateTime.now() + "] [ELS] " + message;
        System.out.println(fullMessage);
        writer.println(fullMessage);
        writer.flush();
    }
}
```

**Kluczowe elementy:**

1. **Singleton Pattern** - jedna instancja w całej aplikacji
2. **Double-Checked Locking** - thread-safe inicjalizacja
3. **Shutdown Hook** - automatyczne zamykanie przy zakończeniu aplikacji
4. **Append Mode** - logi są dopisywane, nie nadpisywane
5. **Flush** - natychmiastowy zapis do pliku

---

## 🛡️ Bezpieczeństwo i Best Practices

### 1. **ThreadLocal Safety**
- Każdy request HTTP ma swój wątek
- `AclContext` używa `ThreadLocal`, więc dane są izolowane
- **ZAWSZE** czyścimy w `finally` - brak memory leaks

### 2. **Fail-Safe**
- Jeśli SQL parsing się nie uda → zwraca oryginał (loguje błąd)
- Jeśli `AclUserProvider` nie istnieje → używa "system-default-user"
- Jeśli `AclContext` nie jest aktywne → SQL bez zmian

### 3. **Performance**
- Proxy Pattern - minimalne opóźnienie
- ThreadLocal - brak synchronizacji
- SQL Rewriting - dzieje się raz na zapytanie

### 4. **Transparency**
- Aplikacja nie musi wiedzieć o ACL
- Brak zmian w kodzie biznesowym
- Dodanie `@RequiresAcl` to jedyna zmiana

---

## 📊 Struktura Tabeli ACL

```sql
CREATE TABLE els_acl_table (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,      -- ID użytkownika
    table_name VARCHAR(50) NOT NULL, -- Nazwa tabeli (np. 'books')
    row_id BIGINT NOT NULL         -- ID rekordu w tabeli
);
```

**Przykładowe dane:**
```sql
-- Alice może zobaczyć książki o ID 1, 2, 3
INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES ('alice', 'books', 1);
INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES ('alice', 'books', 2);
INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES ('alice', 'books', 3);

-- Bob może zobaczyć książki o ID 2, 4
INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES ('bob', 'books', 2);
INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES ('bob', 'books', 4);
```

**Rezultat:**
- Alice wywołuje `findAll()` → widzi książki 1, 2, 3
- Bob wywołuje `findAll()` → widzi książki 2, 4

---

## 🔍 Obsługiwane Typy Zapytań i Statement

### Typy Statement

Biblioteka obsługuje oba typy JDBC Statement, zapewniając pełną ochronę:

| Typ Statement | Obsługa | Opis |
|---------------|---------|------|
| **PreparedStatement** | ✅ Pełna | SQL modyfikowane podczas tworzenia statement |
| **Statement** | ✅ Pełna | SQL modyfikowane podczas wykonania (executeQuery/Update) |
| **CallableStatement** | ⚪ Planowane | Stored procedures - w planach |

### Typy Operacji SQL

| Operacja | Status | Mechanizm Filtrowania |
|----------|--------|----------------------|
| **SELECT** | ✅ Obsługiwane | INNER JOIN do els_acl_table |
| **UPDATE** | ✅ Obsługiwane | WHERE id IN (subquery) |
| **DELETE** | ✅ Obsługiwane | WHERE id IN (subquery) |
| **INSERT** | ⚪ Planowane | Walidacja uprawnień |
| **DDL** | ❌ Bez filtrowania | CREATE, ALTER, DROP, etc. |

### Przykłady Użycia

#### 1. SELECT z PreparedStatement
```java
@RequiresAcl
public List<Book> getBooksByCategory(String category) {
    return jdbcTemplate.query(
        "SELECT * FROM books WHERE category = ?",
        new Object[]{category},
        bookRowMapper
    );
    // SQL automatycznie filtrowane przez ACL
}
```

#### 2. SELECT z Statement
```java
@RequiresAcl
public List<Book> getAllBooks() throws SQLException {
    try (Statement stmt = connection.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM books")) {
        // SQL automatycznie filtrowane przez ACL
        return mapResultSet(rs);
    }
}
```

#### 3. UPDATE z PreparedStatement
```java
@RequiresAcl
public void updateBookPrice(Long id, BigDecimal newPrice) {
    jdbcTemplate.update(
        "UPDATE books SET price = ? WHERE id = ?",
        newPrice, id
    );
    // Zaktualizuje TYLKO jeśli użytkownik ma dostęp do książki
}
```

#### 4. UPDATE z Statement
```java
@RequiresAcl
public void discountFictionBooks() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate(
            "UPDATE books SET price = price * 0.9 WHERE category = 'fiction'"
        );
        // Zaktualizuje TYLKO książki użytkownika
    }
}
```

#### 5. DELETE z PreparedStatement
```java
@RequiresAcl
public void deleteBook(Long id) {
    jdbcTemplate.update("DELETE FROM books WHERE id = ?", id);
    // Usunie TYLKO jeśli użytkownik ma dostęp do książki
}
```

#### 6. DELETE z Statement
```java
@RequiresAcl
public void deleteOldBooks() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
        stmt.executeUpdate("DELETE FROM books WHERE year < 1990");
        // Usunie TYLKO stare książki użytkownika
    }
}
```

### Kompatybilność z Frameworkami

ELS działa transparentnie z popularnymi frameworkami ORM i JDBC:

| Framework/Biblioteka | Kompatybilność | Uwagi |
|---------------------|----------------|-------|
| **Spring Data JPA** | ✅ Pełna | Automatyczna obsługa przez Hibernate |
| **Hibernate** | ✅ Pełna | Wszystkie zapytania przechwytywane |
| **JdbcTemplate** | ✅ Pełna | Obsługa query(), update(), execute() |
| **MyBatis** | ✅ Pełna | O ile używa Spring DataSource |
| **jOOQ** | ✅ Pełna | O ile używa Spring DataSource |
| **Czysty JDBC** | ✅ Pełna | PreparedStatement i Statement |

### Bezpieczeństwo Wielowarstwowe

Dzięki obsłudze zarówno PreparedStatement jak i Statement, biblioteka zapewnia:

1. **Ochrona przed obejściem** - deweloper nie może przypadkowo ominąć ACL używając Statement
2. **Legacy code support** - starszy kod używający Statement jest automatycznie chroniony
3. **Spójność** - wszystkie operacje (SELECT/UPDATE/DELETE) są filtrowane
4. **Fail-safe** - jeśli parsing SQL się nie uda, zapytanie jest odrzucane lub logowane

---

## 🚀 Jak Używać w Aplikacji Klienckiej?

### 1. Dodaj zależność:
```xml
<dependency>
    <groupId>com.els</groupId>
    <artifactId>entity-level-security</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Zaimplementuj `AclUserProvider`:
```java
@Component
public class MyUserProvider implements AclUserProvider {
    @Override
    public String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
```

### 3. Użyj `@RequiresAcl`:
```java
@Service
public class BookService {
    
    @RequiresAcl
    public List<Book> getMyBooks() {
        return bookRepository.findAll(); // Automatycznie filtrowane!
    }
}
```

### 4. Zarządzaj uprawnieniami:
```java
@Service
public class AclManagementService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public void grantAccess(String userId, String tableName, Long rowId) {
        jdbcTemplate.update(
            "INSERT INTO els_acl_table (user_id, table_name, row_id) VALUES (?, ?, ?)",
            userId, tableName, rowId
        );
    }
    
    public void revokeAccess(String userId, String tableName, Long rowId) {
        jdbcTemplate.update(
            "DELETE FROM els_acl_table WHERE user_id = ? AND table_name = ? AND row_id = ?",
            userId, tableName, rowId
        );
    }
}
```

---

## 🎓 Design Patterns Użyte w Projekcie

1. **Proxy Pattern** - `AclDataSourceProxy`, `AclConnectionProxy`, `AclStatementProxy`
2. **Strategy Pattern** - `AclUserProvider`
3. **Singleton Pattern** - `Logger`
4. **Aspect-Oriented Programming** - `AclSecurityAspect`
5. **Template Method** - `BeanPostProcessor`
6. **Factory Pattern** - `ElsAutoConfiguration`
7. **ThreadLocal Pattern** - `AclContext`

---

## 💡 Podsumowanie

**ELS to biblioteka typu "drop-in"** - dodaj zależność, zaimplementuj `AclUserProvider`, dodaj `@RequiresAcl` i masz gotowe Row-Level Security dla SELECT, UPDATE i DELETE.

**Kluczowe zalety:**
- ✅ **Transparentność** - zero zmian w logice biznesowej
- ✅ **Deklaratywność** - jedna adnotacja załatwia sprawę
- ✅ **Kompleksowość** - obsługa PreparedStatement i Statement
- ✅ **Elastyczność** - strategia pobierania użytkownika
- ✅ **Bezpieczeństwo** - ThreadLocal + fail-safe mechanizmy + filtrowanie UPDATE/DELETE
- ✅ **Wydajność** - minimalne opóźnienie

**Aktualnie obsługiwane:**
- ✅ SELECT - filtrowanie przez INNER JOIN
- ✅ UPDATE - filtrowanie przez subquery
- ✅ DELETE - filtrowanie przez subquery
- ✅ PreparedStatement - pełna obsługa
- ✅ Statement - pełna obsługa

---

## 📄 Licencja

MIT License - szczegóły w pliku LICENSE.
