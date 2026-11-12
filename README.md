# Cuidadores de mayores — app web con vulnerabilidades intencionales

> ⚠️ **Solo para aprendizaje.** Esta app contiene 3 vulnerabilidades a propósito. Ejecutala solo en local, sin datos reales, y no la expongas a internet.

**Stack:** Java 17 · Maven · Spring Boot 3 (Web, Data JPA/Hibernate, Security + JWT) · PostgreSQL · React (Vite).

## Cómo ejecutar
```bash
docker compose up -d                       # PostgreSQL en :5432
cd backend && mvn spring-boot:run          # API en :8080
cd frontend && npm install && npm run dev  # Web en :5173
```

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

### 3. Broken Authentication (OWASP A07, CWE-287) — contraseñas, login y tokens

#### 3a. Política de contraseñas débil (CWE-521), enumeración de cuentas (CWE-204) y sin límite de intentos (CWE-307)

**Paso a paso — Enumeración de cuentas:**
1. En el panel **"Ingresar"** de http://localhost:5173, escribí un correo que no exista (ej. `noexiste@demo.com`) y cualquier contraseña (ej. `1234`). Hacé clic en **Ingresar**.
2. Observá el mensaje de error: **`"El usuario no existe"`**.
3. Ahora cambiá el correo por uno real (`ana@demo.com`) con una contraseña equivocada (`1234`) y hacé clic en **Ingresar**.
4. Observá que el mensaje cambia a: **`"Contraseña incorrecta"`** (esto le confirma a un atacante que `ana@demo.com` tiene cuenta en el sistema).

**Paso a paso — Política de contraseñas débil:**
1. Hacé clic en **"No tengo cuenta"** para pasar al formulario **"Crear cuenta"**.
2. Registrate con `prueba@demo.com` y contraseña **`1`** (o `1234`).
3. El sistema acepta la contraseña débil y crea la cuenta inmediatamente.

**Paso a paso — Fuerza bruta sin límite de intentos:**
1. Abrí la consola del navegador (**F12** → **Console**) y ejecutá este script que prueba una lista de contraseñas contra `ana@demo.com`:
   ```js
   for (const pass of ["1234", "admin", "password", "qwerty", "111111", "demo1234"]) {
     const r = await fetch("http://localhost:8080/api/auth/login", {
       method: "POST",
       headers: { "Content-Type": "application/json" },
       body: JSON.stringify({ email: "ana@demo.com", password: pass })
     });
     console.log(pass, "->", r.status, await r.json());
   }
   ```
2. Vas a ver en consola que el servidor permite todos los intentos seguidos sin bloquear ni demorar (`429 Too Many Requests`), hasta que en `"demo1234"` devuelve `200 OK` y entrega el token de Ana.

#### 3b. Secreto de firma JWT débil y hardcodeado (CWE-798 / CWE-321)
En `JwtService.java`, la clave con la que se firman los tokens está escrita en el código (`SECRET = "cuidadores2026"`). Cualquiera con acceso al repositorio puede fabricar un token válido para suplantar a otro usuario sin conocer su contraseña.

**Paso a paso — Suplantación de identidad falsificando un token:**
1. Si estás logueado en la web, hacé clic en **"Salir"**.
2. Con el secreto `"cuidadores2026"` se firma un token para `userId: 1` (`ana@demo.com`). Abrí **F12** → **Console**, pegá este comando con el token ya firmado con esa clave y presioná **Enter**:
   ```js
   localStorage.setItem("session", JSON.stringify({
     userId: 1,
     token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJhbmFAZGVtby5jb20ifQ.4z7fOB26jfXaAbxLcCOantbL2N4CUI_DiLBDr5c_yas"
   }));
   location.reload();
   ```
3. La página se recarga y quedás dentro de la cuenta de **Ana** (aparece el botón **"Editar"** en sus perfiles y te permite modificarlos) sin haber pasado por el login ni conocer su contraseña.

#### 3c. Token sin expiración ni revocación (CWE-613)
En `JwtService.generate`, el token se emite sin fecha de vencimiento (`exp`) y el servidor no invalida tokens al cerrar sesión.

**Paso a paso:**
1. Iniciá sesión con `ana@demo.com` (`demo1234`).
2. En **F12** → **Console**, guardate el token en una variable antes de salir:
   ```js
   window.tokenRobado = JSON.parse(localStorage.getItem("session")).token;
   ```
3. Hacé clic en el botón **"Salir"** arriba a la derecha en la web (la sesión se borra del navegador).
4. Desde la misma consola (**F12**), usá el `tokenRobado` para crear un perfil en nombre de Ana aunque ya haya cerrado sesión:
   ```js
   fetch("http://localhost:8080/api/profiles", {
     method: "POST",
     headers: {
       "Content-Type": "application/json",
       "Authorization": "Bearer " + window.tokenRobado
     },
     body: JSON.stringify({
       nombre: "Perfil creado tras cerrar sesión",
       apellido: "Token sin revocar",
       zona: "Nueva Córdoba",
       estudios: "Demo 3c",
       intereses: "Seguridad",
       descripcion: "El token sigue siendo aceptado por el backend después del logout."
     })
   }).then(() => location.reload());
   ```
5. Al recargarse la página, aparece el nuevo perfil creado porque el token sigue siendo válido indefinidamente.

**Correcciones:** exigir contraseñas de 12+ caracteres; devolver siempre el mismo mensaje genérico (`"Email o contraseña incorrectos"`); bloquear o aplicar retardo tras varios intentos fallidos; cargar una clave de firma aleatoria de 256 bits desde variable de entorno (`JWT_SECRET`); agregar expiración corta (`exp`) y lista de revocación en logout.

## Controles de seguridad ya presentes
- Contraseñas almacenadas con hash BCrypt.
- Tokens JWT firmados.
- CORS limitado a orígenes locales y rutas de escritura protegidas por autenticación.

## Endpoints
`POST /api/auth/register` · `POST /api/auth/login` · `GET /api/profiles` · `GET /api/profiles/{id}` · `POST /api/profiles` · `PUT /api/profiles/{id}`
