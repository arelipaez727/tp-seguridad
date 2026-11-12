# Guía de Demostración de Broken Authentication (OWASP A07, CWE-287) — contraseñas, login y tokenscon Postman: Autenticación y Vulnerabilidades

## 1. Abrir Postman y hacer tu primera solicitud

1. Abrí la aplicación de escritorio de Postman.
2. Si te pide iniciar sesión o seleccionar un workspace, completá esos pasos.
3. Presioná el botón **+** para abrir una nueva solicitud HTTP.
4. A la izquierda del campo de URL, seleccioná **GET**.
5. Pegá esta dirección:
   ```
   http://localhost:8080/api/profiles
   ```
6. Presioná **Send**.

Deberías ver algo parecido a esto:
- **Método / URL:** `GET http://localhost:8080/api/profiles`
- **Estado:** `200 OK`
- **Body (Respuesta):**
```json
[
  {
    "id": 1,
    "ownerId": 1,
    "nombre": "María",
    "apellido": "Gómez",
    "zona": "Nueva Córdoba"
  }
]
```
> *Ejemplo simplificado de respuesta. Los identificadores y el contenido exacto dependen de tu base de datos.*

¡Si aparece `200 OK`, ya realizaste tu primera solicitud en Postman! El procedimiento corresponde al cliente HTTP de la documentación oficial de Postman Docs.

---

## 4. Tu primera vulnerabilidad: enumeración de usuarios
Esta es justamente la prueba que ya empezaste.

### Paso 3. Intentar entrar con un usuario inexistente
1. Abrí otra solicitud en Postman y configurá lo siguiente:
   - **Método:** `POST`
   - **URL:** `http://localhost:8080/api/auth/login`
2. Debajo de la URL vas a encontrar pestañas como *Params*, *Authorization*, *Headers* y *Body*.
3. Entrá a **Body**, elegí **raw** y seleccioná **JSON** en el desplegable.
4. Pegá esto:
   ```json
   {
     "email": "noexiste@demo.com",
     "password": "1234"
   }
   ```
5. Presioná **Send**.

**Resultado esperado:**
- **Estado:** `401 Unauthorized`
- **Body:**
```json
{
  "error": "El usuario no existe"
}
```
> *No te asustes por el código rojo. En este caso significa que el backend rechazó las credenciales, tal como esperábamos.*

### Paso 4. Cambiar solamente el email
1. En la misma solicitud, reemplazá el JSON por:
   ```json
   {
     "email": "ana@demo.com",
     "password": "1234"
   }
   ```
2. Presioná nuevamente **Send**.

**Resultado esperado:**
- **Estado:** `401 Unauthorized`
- **Body:**
```json
{
  "error": "Contraseña incorrecta"
}
```

¡Acá está la primera demostración!  
El servidor devuelve `401` en ambos casos, pero con mensajes diferentes.  
Esto permite descubrir si una dirección de correo está registrada, aunque no sepamos su contraseña.

> **¿Qué le explicás al profesor?**  
> La vulnerabilidad se produce porque el sistema revela información sobre la existencia de las cuentas. Un atacante podría aprovechar las diferencias entre los mensajes de error para identificar usuarios válidos y concentrar sus intentos de acceso sobre esas cuentas.

---

## 5. Segunda vulnerabilidad: contraseñas débiles
Ahora vamos a hacer la prueba desde el frontend, porque es más visual.

1. Abrí `http://localhost:5173`.
2. Hacé clic en **No tengo cuenta**.
3. Escribí un correo que todavía no hayas registrado, por ejemplo `demo-debil-01@demo.com`.
4. Como contraseña, poné solamente `1`.
5. Hacé clic en **Crear cuenta**.

**Resultado esperado:** la aplicación crea la cuenta y te muestra el formulario para registrar un perfil.

> **¿Por qué ocurre?** Porque `AuthController.register()` solo verifica que la contraseña no esté vacía:
> ```java
> if (c.email() == null || c.password() == null || c.password().isBlank())
> ```
> No comprueba su longitud ni su fortaleza.  
> Esto es lo que vamos a corregir en la otra rama.

---

## 6. Tercera vulnerabilidad: intentos ilimitados de login
Volvemos a Postman.

1. Creá una solicitud `POST` a:
   ```
   http://localhost:8080/api/auth/login
   ```
2. En **Body** → **raw** → **JSON**:
   ```json
   {
     "email": "ana@demo.com",
     "password": "incorrecta123"
   }
   ```
3. Ahora presioná el botón **Send** seis veces, observando cada respuesta.

En tu rama `main`, todas deberían devolver `401`. No existe ningún contador ni mecanismo de bloqueo en `AuthController`.

Lo que queremos demostrar es que el sistema no responde con un límite de solicitudes, como `429 Too Many Requests`, ni suspende temporalmente los intentos.

> *No necesitás automatizar cientos de intentos: seis alcanzan para explicar el problema en este laboratorio.*

---

## 7. Cuarta vulnerabilidad: falsificación de JWT
Esta es la prueba que yo elegiría como demostración principal de tu exposición.

En tu código encontré lo siguiente:
```java
private static final String SECRET = "cuidadores2026";
```
Y en `JwtFilter.java` vi que el servidor obtiene el identificador del usuario directamente del JWT, después de comprobar su firma.

Eso significa que, si alguien conoce la clave, puede firmar un token que represente a otra cuenta.

### Paso A. Obtener el ID real de Ana
1. En Postman enviá:
   - **Método:** `POST`
   - **URL:** `http://localhost:8080/api/auth/login`
   - **Body (raw JSON):**
     ```json
     {
       "email": "ana@demo.com",
       "password": "demo1234"
     }
     ```
   *(En el código de `DataSeeder.java`, esa es la contraseña inicial de Ana).*
2. Si no fue cambiada, deberías obtener `200 OK` y una respuesta con este formato:
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiJ9...",
     "userId": 1
   }
   ```
3. Anotá el `userId` real que aparezca. No suponemos que necesariamente sea 1.

### Paso B. Generar un JWT de laboratorio
Podés usar el script `forge_jwt.py` del kit que te preparé anteriormente.

1. Abrí la terminal en su carpeta `demo` y ejecutá, reemplazando `ID_REAL`:
   ```powershell
   py .\forge_jwt.py ID_REAL ana@demo.com
   ```
2. El script utiliza la clave conocida del `main` para construir un JWT de prueba. No necesita conocer la contraseña de Ana.
3. Copiá el token generado.

### Paso C. Probar el acceso sin token
1. En Postman creá una solicitud:
   - **Método:** `POST`
   - **URL:** `http://localhost:8080/api/profiles`
2. En la pestaña **Authorization**, seleccioná **No Auth**.
3. En **Body** → **raw** → **JSON**, pegá:
   ```json
   {
     "nombre": "Demostración",
     "apellido": "Seguridad",
     "zona": "Nueva Córdoba",
     "descripcion": "Prueba de Broken Authentication",
     "estudios": "Seguridad de Software",
     "intereses": "Ciberseguridad"
   }
   ```
4. Presioná **Send**.
5. La solicitud debería ser rechazada con `401` o `403`, porque esta ruta exige autenticación.

### Paso D. Repetir usando el JWT fabricado
Sin cambiar la URL ni el JSON:
1. Abrí la pestaña **Authorization**.
2. En el selector de tipo de autorización elegí **Bearer Token**.
3. Pegá el JWT que generaste en el campo **Token**, sin escribir `Bearer` delante *(Postman agrega automáticamente el encabezado `Authorization: Bearer ...`)*.
4. Presioná **Send**.

**Resultado esperado en main:** `200 OK`, con los datos del nuevo perfil y el identificador de Ana como propietario.

Después actualizá el frontend. ¡Deberías ver el perfil ficticio que acabás de crear!

---

### Lo que acabás de demostrar
La API rechazó una solicitud sin autenticación, pero aceptó un JWT que no fue emitido mediante el login legítimo. Esto ocurre porque el secreto de firma del servidor está expuesto y puede reproducirse.

Al realizar la misma prueba en la rama corregida, el JWT fabricado debería dejar de funcionar.

> ⚠️ *Para esta práctica utilizá únicamente los usuarios ficticios de tu proyecto local. La creación del perfil modifica la base de datos, así que conviene hacerlo una sola vez durante la demostración.*

