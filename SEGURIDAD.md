# Decisiones de seguridad

Este documento describe las medidas aplicadas para corregir la inyección SQL del buscador de perfiles y agregar defensas en profundidad.

## 1. Consulta parametrizada con Spring Data

Se eliminó el SQL construido por concatenación y el uso de `Statement` en `ProfileController`. El buscador ahora llama a `findByZonaContainingIgnoreCase`, un método derivado de Spring Data JPA.

Spring Data genera una consulta con parámetros enlazados. Por lo tanto, el valor de `zona` se interpreta como dato y no puede modificar la estructura de la consulta. Esta es la corrección principal para CWE-89.

## 2. Validación y normalización de entrada

El parámetro `zona` tiene una longitud máxima de 100 caracteres mediante Bean Validation. Antes de buscar también se eliminan los espacios iniciales y finales con `trim()`.

La validación limita entradas anómalas y consumo innecesario de recursos. No reemplaza la parametrización: ambas medidas cumplen funciones diferentes.

## 3. Pruebas de regresión de seguridad

Las pruebas automatizadas comprueban que:

- `zzz' OR '1'='1' --` no devuelve todos los perfiles.
- `zzz'; DELETE FROM profiles; --` no elimina datos.
- La búsqueda parcial y sin distinción de mayúsculas sigue funcionando.
- Una zona de más de 100 caracteres recibe HTTP 400 y no llega al repositorio.

Las pruebas del repositorio usan H2 solamente durante la fase de test y no afectan PostgreSQL.

## 4. Manejo seguro de errores

`GlobalExceptionHandler` devuelve respuestas controladas para errores de validación y de acceso a datos. Las respuestas no incluyen SQL, nombres de tablas, excepciones ni stack traces.

Además, la configuración de Spring desactiva la exposición de mensajes internos, excepciones y stack traces en el endpoint de error.

Los detalles técnicos continúan disponibles en los logs del servidor para diagnóstico, pero no se envían al cliente.

## 5. Privilegios mínimos en PostgreSQL

La aplicación ya no se conecta como el superusuario `postgres`. El script de inicialización crea el rol `cuidadores_app` con estos permisos:

- `CONNECT` sobre la base `cuidadores`.
- `USAGE` sobre el esquema `public`.
- `SELECT`, `INSERT` y `UPDATE` sobre `users` y `profiles`.
- Uso de las secuencias necesarias para generar identificadores.

El rol no recibe permisos de `DELETE`, `CREATE`, `ALTER` ni `DROP`. Esto reduce el impacto de un defecto futuro. Como Hibernate no puede modificar el esquema con este rol, `ddl-auto` se configuró como `validate` y el esquema se crea desde el script administrado por Docker.

Las credenciales locales tienen valores predeterminados para facilitar la ejecución académica, pero pueden reemplazarse con las variables de entorno `DB_USER` y `DB_PASSWORD`. La aplicación usa el puerto local `5433` para no interferir con otras instalaciones de PostgreSQL; puede cambiarse con `DB_PORT`. La API usa `8082` y puede cambiarse con `SERVER_PORT`; el frontend admite `VITE_API_URL` para apuntar a otra URL.

## Ejecución y verificación

```bash
docker compose up -d
cd backend
mvn test
mvn spring-boot:run
```

El frontend se inicia como antes:

```bash
cd frontend
npm install
npm run dev
```

Estas medidas corrigen la inyección SQL, pero no eliminan las otras vulnerabilidades intencionales del proyecto, como IDOR o la configuración débil de autenticación descritas en el README.
