# Cuidadores de mayores — app web con vulnerabilidades intencionales

> ⚠️ **Solo para aprendizaje.** Esta app contiene 3 vulnerabilidades a propósito. Ejecutala solo en local, sin datos reales, y no la expongas a internet.

**Stack:** Java 17 · Maven · Spring Boot 3 (Web, Data JPA/Hibernate, Security + JWT) · PostgreSQL · React (Vite).

## Cómo ejecutar
```bash
docker compose up -d                       # PostgreSQL en :5432
cd backend && mvn spring-boot:run          # API en :8080
cd frontend && npm install && npm run dev  # Web en :5173
```
(Sin Docker: creá una base `cuidadores` con usuario/clave `postgres`, o ajustá `application.properties`.)

Al arrancar por primera vez, el backend carga automáticamente 5 perfiles de ejemplo en distintas zonas (ver `DataSeeder.java`), junto con dos usuarios de prueba: `ana@demo.com` y `luis@demo.com`, ambos con contraseña `demo1234`.

## Comportamiento del buscador por zona
- **Sin texto en el buscador:** se muestra la lista completa de perfiles.
- **Con una zona que tiene perfiles:** se muestran solo los que coinciden.
- **Con una zona sin perfiles registrados:** se muestra el mensaje "No se encontraron perfiles para esa zona."

## Vulnerabilidades (están marcadas con `⚠️` en el código)

### 1. SQL Injection (CWE-89) — `GET /api/profiles?zona=...`
La zona se concatena en una consulta SQL nativa, ejecutada con un `Statement` JDBC plano que admite varias sentencias separadas por `;` (`ProfileController.list`).

**Comprobación desde la interfaz:**
1. Abrí http://localhost:5173. Sin buscar nada, deberías ver los 5 perfiles de ejemplo.
2. Buscá una zona inexistente, por ejemplo `zzz`, y confirmá el mensaje "No se encontraron perfiles para esa zona."
3. Buscá exactamente: `zzz' OR '1'='1' --` (sin comillas invertidas). Deberían aparecer todos los perfiles, a pesar de que la zona no existe.

**Comprobación de consultas apiladas (más avanzada, modifica datos reales):**
- Cambiar la zona de todos los perfiles: `zzz'; UPDATE profiles SET zona='Hackeado'; --`
- Borrar todos los perfiles: `zzz'; DELETE FROM profiles; --`

Hacé una copia de los datos antes de probar estos dos últimos, o volvé a cargarlos reiniciando el backend con la tabla vacía (el `DataSeeder` la vuelve a poblar).

**Corrección:** usar un método derivado de Spring Data (`findByZonaContainingIgnoreCase`) o una consulta con parámetros nombrados, y nunca ejecutar texto de usuario como lote de sentencias.

### 2. IDOR (CWE-639) — `PUT /api/profiles/{id}`
Se exige estar logueado, pero no se verifica que el perfil sea tuyo (`ProfileController.update`).

**Comprobación desde la interfaz:**
1. Iniciá sesión como `ana@demo.com` (clave `demo1234`) y anotá el `id` de uno de sus perfiles (se ve en la pestaña Network del navegador al cargar la página).
2. Cerrá sesión e iniciá sesión como `luis@demo.com`.
3. Confirmá que el botón "Editar" no aparece sobre los perfiles de Ana: la interfaz lo oculta porque no son tuyos.
4. Copiá el token de Luis desde Local Storage (F12 → Application → Local Storage → `session`) y ejecutá:
   ```bash
   curl -X PUT http://localhost:8080/api/profiles/<ID_DEL_PERFIL_DE_ANA> \
     -H "Authorization: Bearer <TOKEN_DE_LUIS>" \
     -H "Content-Type: application/json" \
     -d '{"nombre":"Hackeado","apellido":"X","zona":"Z","descripcion":"","intereses":"","estudios":""}'
   ```
5. Recargá la web: el perfil de Ana ahora aparece modificado por Luis, sin tener permiso.

**Corrección:** comparar `ownerId` con el usuario del token y responder 403 si no coincide.

### 3. Broken Authentication (OWASP A07) — contraseñas y tokens

#### 3a. Política de contraseñas débil y sin límite de intentos
**Comprobación:** registrate con la contraseña `1`: el sistema la acepta. Después intentá iniciar sesión con contraseñas incorrectas varias veces seguidas: no hay bloqueo ni demora creciente.

#### 3b. Secreto de firma JWT débil y hardcodeado
**Comprobación:** en `JwtService.java`, el secreto con el que se firman todos los tokens es un texto corto y fijo (`SECRET = "cuidadores2026"`), visible en el repositorio. Quien lo conozca puede firmar un token válido para cualquier usuario.

#### 3c. Token sin expiración ni revocación
**Comprobación:** iniciá sesión, copiá el token de Local Storage, hacé clic en "Salir", y usá ese mismo token en un `curl` a `POST /api/profiles`: sigue funcionando, porque el token no tiene fecha de expiración ni se invalida del lado del servidor.

**Correcciones:** exigir contraseñas de 12+ caracteres y bloqueo tras varios intentos fallidos; clave de firma aleatoria desde variable de entorno; expiración corta (15 min) + refresh token + lista de revocación.

## Controles de seguridad ya presentes
- Contraseñas almacenadas con hash BCrypt.
- Tokens JWT firmados.
- CORS limitado a orígenes locales y rutas de escritura protegidas por autenticación.

## Endpoints
`POST /api/auth/register` · `POST /api/auth/login` · `GET /api/profiles` · `GET /api/profiles/{id}` · `POST /api/profiles` · `PUT /api/profiles/{id}`
