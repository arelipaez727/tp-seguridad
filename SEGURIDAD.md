# Decisiones de seguridad

Este documento describe las medidas aplicadas para corregir las vulnerabilidades del proyecto (**SQL Injection**, **IDOR** y **Broken Authentication**) y agregar defensas en profundidad.

---

## Parte I — Corrección de SQL Injection (CWE-89) y defensas en base de datos

### 1. Consulta parametrizada con Spring Data

Se eliminó el SQL construido por concatenación y el uso de `Statement` en `ProfileController`. El buscador ahora llama a `findByZonaContainingIgnoreCase`, un método derivado de Spring Data JPA.

Spring Data genera una consulta con parámetros enlazados. Por lo tanto, el valor de `zona` se interpreta como dato y no puede modificar la estructura de la consulta. Esta es la corrección principal para CWE-89.

### 2. Validación y normalización de entrada

El parámetro `zona` tiene una longitud máxima de 100 caracteres mediante Bean Validation. Antes de buscar también se eliminan los espacios iniciales y finales con `trim()`.

La validación limita entradas anómalas y consumo innecesario de recursos. No reemplaza la parametrización: ambas medidas cumplen funciones diferentes.

### 3. Pruebas de regresión de seguridad

Las pruebas automatizadas comprueban que:

- `zzz' OR '1'='1' --` no devuelve todos los perfiles.
- `zzz'; DELETE FROM profiles; --` no elimina datos.
- La búsqueda parcial y sin distinción de mayúsculas sigue funcionando.
- Una zona de más de 100 caracteres recibe HTTP 400 y no llega al repositorio.

Las pruebas del repositorio usan H2 solamente durante la fase de test y no afectan PostgreSQL.

### 4. Manejo seguro de errores

`GlobalExceptionHandler` devuelve respuestas controladas para errores de validación y de acceso a datos. Las respuestas no incluyen SQL, nombres de tablas, excepciones ni stack traces.

Además, la configuración de Spring desactiva la exposición de mensajes internos, excepciones y stack traces en el endpoint de error.

Los detalles técnicos continúan disponibles en los logs del servidor para diagnóstico, pero no se envían al cliente.

### 5. Privilegios mínimos en PostgreSQL

La aplicación ya no se conecta como el superusuario `postgres`. El script de inicialización crea el rol `cuidadores_app` con estos permisos:

- `CONNECT` sobre la base `cuidadores`.
- `USAGE` sobre el esquema `public`.
- `SELECT`, `INSERT` y `UPDATE` sobre `users` y `profiles`.
- Uso de las secuencias necesarias para generar identificadores.

El rol no recibe permisos de `DELETE`, `CREATE`, `ALTER` ni `DROP`. Esto reduce el impacto de un defecto futuro. Como Hibernate no puede modificar el esquema con este rol, `ddl-auto` se configuró como `validate` y el esquema se crea desde el script administrado por Docker.

Las credenciales locales tienen valores predeterminados para facilitar la ejecución académica, pero pueden reemplazarse con las variables de entorno `DB_USER` y `DB_PASSWORD`. La aplicación usa el puerto local `5433` para no interferir con otras instalaciones de PostgreSQL; puede cambiarse con `DB_PORT`. La API usa `8082` y puede cambiarse con `SERVER_PORT`; el frontend admite `VITE_API_URL` para apuntar a otra URL.

---

## Parte II — Corrección de IDOR (CWE-639)

En `ProfileController.update` (`PUT /api/profiles/{id}`), se incorporó la verificación de propiedad del recurso antes de permitir cualquier modificación:

- Se obtiene el identificador del usuario autenticado desde el token JWT (`(Long) auth.getPrincipal()`).
- Se compara con el `ownerId` del perfil almacenado en la base de datos.
- Si no coinciden, el servidor rechaza la operación con `403 Forbidden` (`"No tenés permiso para modificar este perfil"`).

---

## Parte III — Corrección de Broken Authentication (OWASP A07 / CWE-287)

Esta sección detalla las correcciones aplicadas sobre el flujo de registro, inicio de sesión y emisión/verificación de tokens JWT en `AuthController.java`, `LoginAttemptService.java` y `JwtService.java`.

### Corrección 1 — Política de contraseñas en el registro (CWE-521)

**Problema original:** `AuthController.register` solo verificaba que la contraseña no estuviera vacía (`!c.password().isBlank()`), permitiendo registrar cuentas con claves triviales como `"1"` o `"1234"`.

**Solución aplicada (`AuthController.java`):**
- **Longitud mínima de 12 caracteres:** se rechaza cualquier contraseña con `password.length() < 12`.
- **Rechazo de contraseñas comunes:** se valida la clave (normalizada en minúsculas) contra una lista de contraseñas débiles/predecibles (`COMMON_PASSWORDS`, como `"123456789012"`, `"password1234"`, `"qwerty123456"`, `"aaaaaaaaaaaa"`, `"contrasena123"`).
- Si no cumple ambos requisitos, el endpoint responde con `400 Bad Request` (`"La contraseña debe tener al menos 12 caracteres y no ser común"`).
- **Almacenamiento seguro:** se conserva el hash con **BCrypt** (`BCryptPasswordEncoder`) antes de persistir el usuario en PostgreSQL.

### Corrección 2 — Protección contra intentos repetidos / fuerza bruta (CWE-307)

**Problema original:** `POST /api/auth/login` permitía realizar intentos fallidos ilimitados sobre una misma cuenta sin aplicar bloqueos ni demoras.

**Solución aplicada (`LoginAttemptService.java` y `AuthController.java`):**
Se incorporó el servicio `LoginAttemptService` para controlar los intentos de inicio de sesión por dirección de correo (normalizada con `trim().toLowerCase()`):
- **Registro de fallos por correo:** cada vez que falla la autenticación (`recordFailure(email)`), se incrementa el contador de intentos fallidos para esa cuenta.
- **Bloqueo temporal tras 5 fallos (`MAX_ATTEMPTS = 5`):** al alcanzar cinco intentos fallidos consecutivos, la cuenta queda bloqueada temporalmente.
- **Duración configurada para el laboratorio:** **2 minutos** (`BLOCK_TIME = 2 * 60 * 1000L`), lo que permite demostrar tanto el bloqueo inmediato como la recuperación en clase.
- **Respuesta ante cuenta bloqueada:** mientras el bloqueo está activo (`isBlocked(email)`), el login rechaza el pedido con código **`429 Too Many Requests`** y el mensaje `"Demasiados intentos. Intentá nuevamente en 2 minutos"`, sin siquiera evaluar la contraseña.
- **Reinicio tras login exitoso:** cuando el usuario ingresa con las credenciales válidas, se invoca `recordSuccess(email)` para limpiar el contador de fallos.

### Corrección 3 — Mensajes de error genéricos contra enumeración de usuarios (CWE-204)

**Problema original:** el login respondía con mensajes distintos cuando el correo no existía (`"El usuario no existe"`) y cuando la clave no coincidía (`"Contraseña incorrecta"`), permitiendo a un atacante descubrir qué correos estaban registrados en la plataforma.

**Solución aplicada (`AuthController.java`):**
- En `POST /api/auth/login`, tanto si el usuario no existe (`u == null`) como si la contraseña no coincide (`!enc.matches(...)`), se registra el fallo y se devuelve exactamente el mismo código y mensaje genérico:
  - **Estado:** `401 Unauthorized`
  - **Mensaje:** `"Email o contraseña incorrectos"`
- De forma complementaria, en `POST /api/auth/register`, si el correo ya existe se devuelve un mensaje genérico (`"No se pudo crear la cuenta"`).

### Corrección 4 — Secreto de firma JWT seguro por variable de entorno y expiración del token (CWE-798, CWE-321, CWE-613)

**Problema original:**
1. En `JwtService.java` la clave de firma estaba hardcodeada en el código fuente (`SECRET = "cuidadores2026"`), por lo que cualquiera con acceso al repositorio podía falsificar tokens válidos para suplantar a cualquier usuario.
2. Los tokens emitidos no tenían fecha de expiración (`exp`), permaneciendo válidos indefinidamente.

**Solución aplicada (`JwtService.java`):**

- **Antes:**
  ```java
  private static final String SECRET = "cuidadores2026";
  private final SecretKey key = Keys.hmacShaKeyFor(Arrays.copyOf(SECRET.getBytes(StandardCharsets.UTF_8), 32));
  ```
- **Después:**
  ```java
  public JwtService(@Value("${JWT_SECRET}") String secretBase64) {
      byte[] secretBytes = Decoders.BASE64.decode(secretBase64);
      if (secretBytes.length < 32) {
          throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 bytes");
      }
      this.key = Keys.hmacShaKeyFor(secretBytes);
  }
  ```
- **Propiedades de la nueva clave de firma:**
  - **Se genera aleatoriamente** con entropía criptográfica.
  - **Tiene al menos 256 bits (32 bytes)**, validados al iniciar el servicio.
  - **Se suministra mediante la variable de entorno `JWT_SECRET`** (codificada en Base64).
  - **No queda escrita en el código fuente ni en archivos versionados del repositorio.**
- **Incorporación de identificador único (`jti`) y vencimiento en el JWT (`exp`):**
  ```java
  .id(UUID.randomUUID().toString())
  .issuedAt(new Date(now))
  .expiration(new Date(now + 15 * 60 * 1000L))
  .signWith(key, Jwts.SIG.HS256)
  ```
  Cada token emitido incluye un identificador único (`jti`) y tiene una **duración máxima de 15 minutos**, reduciendo la ventana de exposición en caso de robo del token.

### Corrección 5 — Revocación individual de tokens JWT en el cierre de sesión (CWE-613)

**Problema original:** Al hacer clic en "Salir", el token únicamente se eliminaba del `localStorage` del navegador, pero seguía siendo aceptado por el servidor si alguien lo había copiado previamente.

**Solución aplicada (`JwtService.java`, `AuthController.java` y `frontend/src/App.jsx`):**
- **Identificador único por token (`jti`):** al emitirse un JWT en `JwtService.generate`, se le asigna un `UUID` único mediante `.id(UUID.randomUUID().toString())`.
- **Lista de revocación en el servidor (`revokedTokens`):** `JwtService` mantiene un mapa concurrente (`ConcurrentHashMap<String, Long>`) que asocia el identificador `jti` del token revocado con su timestamp de expiración, limpiando automáticamente las entradas vencidas.
- **Endpoint `POST /api/auth/logout` (`AuthController.java`):** recibe el encabezado `Authorization: Bearer <token>`, verifica el token e invoca `jwt.revoke(token)` para invalidar ese JWT específico en el servidor. El botón **"Salir"** del frontend ahora llama a este endpoint antes de limpiar la sesión local.
- **Verificación en cada solicitud (`JwtService.parse`):** antes de autorizar cualquier pedido en `JwtFilter`, `parse(token)` comprueba si su `jti` figura en `revokedTokens`; si fue revocado, lanza una `JwtException` y el servidor rechaza el acceso (`401`/`403`).

---

## Ejecución y verificación

1. **Iniciar la base de datos (si se usa Docker):**
   ```bash
   docker compose up -d
   ```
2. **Generar y configurar la clave `JWT_SECRET` en la terminal (PowerShell) e iniciar el backend:**
   ```powershell
   py -c "import secrets,base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
   $env:JWT_SECRET="PEGAR_AQUI_LA_CLAVE_GENERADA"
   cd backend
   mvn test
   mvn spring-boot:run
   ```
3. **Iniciar el frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
