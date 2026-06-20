# Guia de Migração — Ticketmaster (Quarkus → Spring Boot)

Migração feature a feature. Cada seção descreve exatamente o que criar naquela etapa: migration Flyway, entities, repositórios, DTOs, services e controllers. Ao fim de cada feature o app compila e os endpoints funcionam.

Referências de padrão: `ARCHITECTURE-SPRING.md` (como escrever), `ARCHITECTURE-ORIGINAL.md` (o que existia no Quarkus).

---

## F1 — Eventos & Assentos

**Resultado:** `POST /events`, `GET /events`, `GET /events/{id}`, `GET /events/{id}/seats` — todos públicos, sem auth.

### Flyway — `V1__create_schema.sql`

```sql
CREATE TABLE IF NOT EXISTS tb_events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS tb_seats (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT,
    name VARCHAR(255),
    status VARCHAR(50),
    CONSTRAINT fk_seats_event FOREIGN KEY (event_id) REFERENCES tb_events(id)
);
```

### Entities — `com.ticketmaster.entity`

`SeatStatus.java`
```java
public enum SeatStatus { AVAILABLE, BOOKED }
```

`EventEntity.java` — `@Table(name = "tb_events", schema = "public")`
- `id` — `@GeneratedValue(strategy = IDENTITY)`
- `name`, `description` — `String`
- `seats` — `@OneToMany(mappedBy = "event") Set<SeatEntity>`
- Construtor: `EventEntity(String name, String description)`

`SeatEntity.java` — `@Table(name = "tb_seats", schema = "public")`
- `id` — `@GeneratedValue(strategy = IDENTITY)`
- `event` — `@ManyToOne @JoinColumn(name = "event_id")`
- `name` — `String`
- `status` — `@Enumerated(EnumType.STRING) SeatStatus`
- Construtor: `SeatEntity(EventEntity event, String name, SeatStatus status)`

### Repositories — `com.ticketmaster.repository`

```java
public interface EventRepository extends JpaRepository<EventEntity, Long> {}

public interface SeatRepository extends JpaRepository<SeatEntity, Long> {
    Page<SeatEntity> findByEvent(EventEntity event, Pageable pageable);
}
```

### DTOs — `com.ticketmaster.controller.dto`

```java
public record EventSettingDto(@NotNull @Min(1) Integer numberOfSeats) {}

public record CreateEventDto(
    @NotBlank String name,
    @NotBlank String description,
    @NotNull @Valid EventSettingDto settings) {}

public record EventDto(Long id, String name, String description) {
    public static EventDto fromEntity(EventEntity entity) {
        return new EventDto(entity.getId(), entity.getName(), entity.getDescription());
    }
}

public record SeatDto(Long seatId, String name, String status) {
    public static SeatDto fromEntity(SeatEntity entity) {
        return new SeatDto(entity.getId(), entity.getName(), entity.getStatus().name());
    }
}

public record PaginationDto(int page, int pageSize, int totalPages, long totalItems) {}

public record ApiListDto<T>(List<T> data, PaginationDto pagination) {}
```

### Service — `com.ticketmaster.service.EventService`

```java
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public EventDto createEvent(CreateEventDto dto) {
        EventEntity event = new EventEntity(dto.name(), dto.description());
        eventRepository.save(event);

        for (int i = 0; i < dto.settings().numberOfSeats(); i++) {
            seatRepository.save(new SeatEntity(event, "S" + i, SeatStatus.AVAILABLE));
        }

        return EventDto.fromEntity(event);
    }

    public ApiListDto<EventDto> findAll(int page, int pageSize) {
        Page<EventEntity> result = eventRepository.findAll(PageRequest.of(page, pageSize));
        List<EventDto> events = result.getContent().stream()
                .map(EventDto::fromEntity)
                .toList();
        return new ApiListDto<>(events,
                new PaginationDto(page, pageSize, result.getTotalPages(), result.getTotalElements()));
    }

    public Optional<EventDto> findById(Long id) {
        return eventRepository.findById(id).map(EventDto::fromEntity);
    }

    public ApiListDto<SeatDto> findAllSeats(Long eventId, int page, int pageSize) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        Page<SeatEntity> result = seatRepository.findByEvent(event, PageRequest.of(page, pageSize));
        List<SeatDto> seats = result.getContent().stream()
                .map(SeatDto::fromEntity)
                .toList();
        return new ApiListDto<>(seats,
                new PaginationDto(page, pageSize, result.getTotalPages(), result.getTotalElements()));
    }
}
```

> `RuntimeException` no `findById` é temporário — substituir por `ResourceNotFoundException` quando F2 criar a hierarquia de erros.

### Controller — `com.ticketmaster.controller.EventController`

```java
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ApiListDto<EventDto> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return eventService.findAll(page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto createEvent(@RequestBody @Valid CreateEventDto dto) {
        return eventService.createEvent(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventDto> getEvent(@PathVariable Long id) {
        return eventService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/seats")
    public ApiListDto<SeatDto> getSeats(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return eventService.findAllSeats(id, page, pageSize);
    }
}
```

### Como testar (F1)

```bash
# criar evento com 5 assentos
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"name":"Rock Show","description":"Show de rock","settings":{"numberOfSeats":5}}'

# listar eventos (paginado)
curl "http://localhost:8080/events?page=0&pageSize=10"

# detalhe do evento
curl http://localhost:8080/events/1

# listar assentos
curl "http://localhost:8080/events/1/seats?page=0&pageSize=10"
```

---

## F2 — Usuários & Admin

**Resultado:** `POST /users`, `POST /setup-admin` — públicos. `GET /users` existe mas só será protegido na F5 (security).

### Flyway — `V2__users.sql`

```sql
CREATE TABLE IF NOT EXISTS tb_scopes (
    scope_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tb_roles (
    role_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tb_role_scopes (
    role_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, scope_id),
    CONSTRAINT fk_role_scopes_role FOREIGN KEY (role_id) REFERENCES tb_roles(role_id),
    CONSTRAINT fk_role_scopes_scope FOREIGN KEY (scope_id) REFERENCES tb_scopes(scope_id)
);

CREATE TABLE IF NOT EXISTS tb_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    role_id BIGINT NOT NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES tb_roles(role_id)
);
```

### Flyway — `V3__seed.sql`

```sql
INSERT INTO tb_scopes (scope_id, name) VALUES
    (1, 'events:list'), (2, 'events:read'), (3, 'seats:list'),
    (4, 'bookings:reserve'), (5, 'bookings:confirm'), (6, 'bookings:reject')
ON CONFLICT (scope_id) DO NOTHING;

INSERT INTO tb_roles (role_id, name) VALUES
    (1, 'user'), (2, 'admin'), (3, 'payment_gtw')
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO tb_role_scopes (role_id, scope_id) VALUES
    (1,1),(1,2),(1,3),(1,4)
ON CONFLICT (role_id, scope_id) DO NOTHING;

INSERT INTO tb_role_scopes (role_id, scope_id) VALUES
    (3,5),(3,6)
ON CONFLICT (role_id, scope_id) DO NOTHING;
```

### Entities

`ScopeEntity` — `tb_scopes`, `scope_id` IDENTITY
`RoleEntity` — `tb_roles`, `role_id` IDENTITY; `@ManyToMany` `tb_role_scopes` → `Set<ScopeEntity> scopes`
`UserEntity` — `tb_users`, `id` IDENTITY; `username/email @Column(unique=true)`; `password`; `@ManyToOne @JoinColumn(name="role_id") RoleEntity role`

### Repositories

```java
public interface ScopeRepository extends JpaRepository<ScopeEntity, Long> {}

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);
}

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsernameOrEmail(String username, String email);
}
```

### DTOs

```java
public record CreateUserDto(@NotBlank String username, @NotBlank String email, @NotBlank String password) {}
public record CreateAdminRequest(@NotBlank String username, @NotBlank String email, @NotBlank String password) {}
```

### Exceptions — `com.ticketmaster.exception` (introduzir nesta feature)

```java
public class TicketMasterException extends RuntimeException {
    private final String title;
    private final String detail;
    private final int status;
    // getters + construtor
}
public class CreateEntityException extends TicketMasterException { /* status 422 */ }
public class AdminException extends TicketMasterException { /* status 422 */ }
public class ResourceNotFoundException extends TicketMasterException { /* status 422 */ }

public record ExceptionResponse(String type, String title, String detail,
                                Integer status, List<InvalidParamResponse> invalidParams) {}
public record InvalidParamResponse(String name, String reason) {}
```

`GlobalExceptionHandler` com `@RestControllerAdvice`:
- `TicketMasterException` → status do próprio objeto
- `MethodArgumentNotValidException` → 400

Atualizar `EventService.findAllSeats` para usar `ResourceNotFoundException` no lugar de `RuntimeException`.

### Services

`UserService.createUser`: `existsByUsernameOrEmail` → `CreateEntityException`; senão `passwordEncoder.encode(password)`, role = `"user"`.
`AdminService.setupAdminUser`: `userRepository.count() > 0` → `AdminException`; senão cria com role `"admin"`.

> `PasswordEncoder` vem de um `@Bean BCryptPasswordEncoder` em `AppConfig` (sem security ainda — só o encoder).

### Controllers

`UserController`: `POST /users` → 201, `GET /users` → 200 (sem proteção por ora).
`AdminController`: `POST /setup-admin` → 200.

---

## F3 — Reservas (sem autenticação)

**Resultado:** `POST /bookings`, `POST /bookings/confirm`, `POST /bookings/reject`. `userId` vem no body por enquanto (refatorado na F5).

### Flyway — `V4__bookings.sql`

```sql
CREATE TABLE IF NOT EXISTS tb_bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    status VARCHAR(50),
    booked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES tb_users(id)
);

CREATE TABLE IF NOT EXISTS tb_tickets (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID,
    seat_id BIGINT,
    booking_id BIGINT,
    CONSTRAINT fk_tickets_seat FOREIGN KEY (seat_id) REFERENCES tb_seats(id),
    CONSTRAINT fk_tickets_booking FOREIGN KEY (booking_id) REFERENCES tb_bookings(id)
);
```

### Entities

`BookingStatus.java`
```java
public enum BookingStatus { PENDING, CONFIRMED, EXPIRED, REJECTED, CANCELLED }
```

`BookingEntity` — `tb_bookings`, `id` IDENTITY; `@ManyToOne user`; `@Enumerated status`; `bookedAt`; `@CreationTimestamp createdAt`; `@UpdateTimestamp updatedAt`.
`TicketEntity` — `tb_tickets`, `id` IDENTITY; `UUID externalId`; `@ManyToOne seat`; `@ManyToOne booking`.

### Repositories

```java
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") Long id);
}

public interface TicketRepository extends JpaRepository<TicketEntity, Long> {
    List<TicketEntity> findByBookingId(Long bookingId);
}
```

Adicionar em `SeatRepository`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from SeatEntity s where s.id = :id")
Optional<SeatEntity> findByIdForUpdate(@Param("id") Long id);
```

### Exceptions (adicionar)

```java
public class SeatAlreadyBookedException extends TicketMasterException { /* 422 */ }
public class UpdateBookingException extends TicketMasterException { /* 422 */ }
```

### DTOs

```java
public record ReserveSeatDto(@NotNull Long seatId) {}
public record CreateBookingDto(
    @NotNull Long userId,        // temporário — removido na F5
    @NotNull Long eventId,
    @NotNull @Size(min = 1) Set<ReserveSeatDto> seats) {}
public record ConfirmBookingDto(@NotNull @Min(1) Long bookingId) {}
public record RejectBookingDto(@NotNull @Min(1) Long bookingId) {}
public record BookingResponseDto(Long bookingId) {}
```

### Services

`BookingService.createBooking(CreateBookingDto dto)` `@Transactional`:
1. Valida user e event existem (lança `ResourceNotFoundException`).
2. Para cada seat: `seatRepository.findByIdForUpdate` → se `BOOKED` → `SeatAlreadyBookedException`.
3. Cria `BookingEntity(PENDING, bookedAt=now, user)` → save.
4. Para cada seat: cria `TicketEntity(UUID.randomUUID(), seat, booking)` → save; seat `status=BOOKED` → save.
5. `bookingExpirationService.scheduleExpirationCheck(bookingId)` — **stub por ora** (só loga).
6. Retorna `booking.getId()`.

`UpdateBookingStatusService.updateBookingStatus(Long bookingId, BookingStatus status)` `@Transactional`:
- `bookingRepository.findByIdForUpdate` → se não `PENDING` → `UpdateBookingException`.
- Atualiza status → save.

`ExpireBookingService.expireBookings(Long bookingId)` `@Transactional`:
- Lock booking → se `PENDING` → `EXPIRED`; `ticketRepository.findByBookingId` → seats `BOOKED→AVAILABLE`.

`BookingExpirationService.scheduleExpirationCheck(Long bookingId)`:
- Stub: `log.info("Expiration scheduled for booking {}", bookingId)`. Implementado de verdade na F6.

### Controller

`BookingController`:
- `POST /bookings` → 200 `BookingResponseDto`
- `POST /bookings/confirm` → 204
- `POST /bookings/reject` → 204

---

## F4 — Apps & Client Credentials (sem auth)

**Resultado:** `POST /apps` cria uma aplicação OAuth com scopes.

### Flyway — `V5__apps.sql`

```sql
CREATE TABLE IF NOT EXISTS tb_apps (
    app_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    client_id UUID NOT NULL UNIQUE,
    client_secret VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_apps_scopes (
    app_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    PRIMARY KEY (app_id, scope_id),
    CONSTRAINT fk_apps_scopes_app FOREIGN KEY (app_id) REFERENCES tb_apps(app_id),
    CONSTRAINT fk_apps_scopes_scope FOREIGN KEY (scope_id) REFERENCES tb_scopes(scope_id)
);
```

### Entity

`AppEntity` — `tb_apps`, `app_id` IDENTITY; `name`, `UUID clientId`, `clientSecret`; `@ManyToMany tb_apps_scopes → Set<ScopeEntity>`.

### Repository

```java
public interface AppRepository extends JpaRepository<AppEntity, Long> {
    Optional<AppEntity> findByClientId(UUID clientId);
}
```

### DTOs

```java
public record CreateAppDto(@NotBlank String name, @NotEmpty Set<String> scopes) {}
public record CreateAppResponse(Long appId, UUID clientId, String clientSecret) {}
```

### Service

`AppService.createApp(CreateAppDto dto)`:
- `clientId = UUID.randomUUID()`; gerar `clientSecret` aleatório.
- Persistir `clientSecret` encodado (`passwordEncoder.encode`).
- Resolver scopes pelo nome via `ScopeRepository`.
- Retorna `CreateAppResponse` com `clientSecret` em claro (só uma vez).

### Controller

`AppController`: `POST /apps` → 201. Sem proteção por ora (protegido na F5).

---

## F5 — Segurança & Autenticação

**Resultado:** `POST /auth/token` (password + client_credentials); todos os endpoints protegidos com `@PreAuthorize`; `BookingController` refatorado para pegar `userId` do JWT.

### pom.xml — adicionar de volta

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

### application.properties — adicionar

```properties
jwt.issuer=ticketmaster
jwt.public-key=classpath:publicKey.pem
jwt.private-key=classpath:rsaPrivateKey.pem
jwt.expires-in-seconds=300
```

### Config — `SecurityConfig`

Beans: `JwtDecoder` (Nimbus, valida issuer), `JwtEncoder` (Nimbus, assina RS256), `PasswordEncoder` (BCrypt).
`JwtAuthenticationConverter`: claim `groups` → authorities sem prefixo.
`SecurityFilterChain`: `permitAll` em `/auth/**`, `POST /users`, `POST /setup-admin`, `GET /events`; resto autenticado. `@EnableMethodSecurity`.

Mover `PasswordEncoder @Bean` de `AppConfig` para `SecurityConfig` (ou manter separado).

### DTOs

```java
public record LoginRequestDto(
    @NotBlank String grantType,
    @NotBlank @JsonProperty("identifier") @JsonAlias({"username","clientId"}) String identifier,
    @NotBlank @JsonProperty("secret") @JsonAlias({"password","clientSecret"}) String secret) {}

public record AccessTokenResponseDto(String accessToken, Long expiresIn) {}
```

### Services

Interface `TokenStrategy { AccessTokenResponseDto generateToken(String identifier, String secret); }`.

`PasswordGrantTokenStrategy`:
1. `userRepository.findByUsername` → `LoginException` se não achar.
2. `passwordEncoder.matches` → `LoginException` se errado.
3. `groups = {role.name} ∪ {scope.name...}`.
4. Assinar JWT (claims: `iss`, `upn`, `sub=userId`, `groups`, `email`, `exp=now+300`).

`ClientCredentialsTokenStrategy`:
1. `appRepository.findByClientId(UUID.fromString(identifier))` → `LoginException`.
2. `passwordEncoder.matches`.
3. `groups = {scope.name...}`.
4. Assinar JWT (`upn=clientId`, `sub=appId`, `groups`, `app_name`, `exp=now+300`).

`AccessTokenService`: `Map<String, TokenStrategy>` → roteia por `grantType`. `"password"` ou `"client_credentials"`; senão `LoginException`.

Construção do JWT:
```java
JwtClaimsSet claims = JwtClaimsSet.builder()
    .issuer("ticketmaster")
    .issuedAt(Instant.now())
    .expiresAt(Instant.now().plusSeconds(300))
    .subject(subject)
    .claim("upn", upn)
    .claim("groups", groups)
    .claim("email", email)   // ou .claim("app_name", appName)
    .build();
String token = jwtEncoder.encode(JwtEncoderParameters.from(
    JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
```

Adicionar `LoginException` à hierarquia (status 401).

### Controllers — refatorar

Adicionar `@PreAuthorize` em todos os endpoints (tabela do ROADMAP.md).
`BookingController.createBooking`: remover `userId` do body; usar `@AuthenticationPrincipal Jwt jwt` → `Long.valueOf(jwt.getSubject())`.
`CreateBookingDto`: remover campo `userId`.

`AuthController`: `POST /auth/token` → chama `accessTokenService.getAccessToken(dto.grantType(), dto.identifier(), dto.secret())`.

---

## F6 — Expiração via RabbitMQ

**Resultado:** booking `PENDING` expira automaticamente após TTL, liberando assentos.

### docker-compose.yml — adicionar

```yaml
rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
```

### pom.xml — adicionar

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### application.properties — adicionar

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

booking.rabbit.exchange=booking.exchange
booking.rabbit.wait-queue=booking.wait
booking.rabbit.check-queue=booking.check
booking.rabbit.wait-routing-key=booking.wait.key
booking.rabbit.check-routing-key=booking.check.key
booking.expiration.check.seconds=30
```

### Config — `RabbitConfig`

`DirectExchange`, fila `booking.wait` (TTL + DLX → `booking.check`), fila `booking.check`, bindings, `Jackson2JsonMessageConverter`.

### DTO — `com.ticketmaster.messaging.dto`

```java
public record CheckBookingStateDto(Long bookingId) {}
```

### Services — implementar o stub

`BookingExpirationService.scheduleExpirationCheck`:
```java
rabbitTemplate.convertAndSend(exchange, waitRoutingKey, new CheckBookingStateDto(bookingId));
```

### Listener — `com.ticketmaster.messaging.CheckBookingListener`

```java
@RabbitListener(queues = "${booking.rabbit.check-queue}")
public void onMessage(CheckBookingStateDto dto) {
    expireBookingService.expireBookings(dto.bookingId());
}
```
