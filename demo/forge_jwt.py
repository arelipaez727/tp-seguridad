# usado para demostración directamente en la terminal. sino usar la opción de usar la colsola del front
import base64
import hashlib
import hmac
import json
import sys
import time

# LABORATORIO LOCAL: clave conocida de JwtService.java
SECRET = "cuidadores2026"

def base64url(datos):
    return base64.urlsafe_b64encode(datos).rstrip(b"=").decode("utf-8") 

def crear_jwt(user_id, email):
    # Reproduce Arrays.copyOf(SECRET.getBytes(UTF_8), 32)
    clave = SECRET.encode("utf-8").ljust(32, b"\x00")

    header = {
        "alg": "HS256"
    }

    payload = {
        "sub": str(user_id),
        "email": email,
        "iat": int(time.time())
    }

    header_b64 = base64url(
        json.dumps(header, separators=(",", ":")).encode("utf-8")
    )
    payload_b64 = base64url(
        json.dumps(payload, separators=(",", ":")).encode("utf-8")
    )

    mensaje = f"{header_b64}.{payload_b64}"

    firma = hmac.new(
        clave,
        mensaje.encode("utf-8"),
        hashlib.sha256
    ).digest()

    return f"{mensaje}.{base64url(firma)}"

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Uso: py forge_jwt.py ID_USUARIO EMAIL")
        sys.exit(1)

    user_id = sys.argv[1]
    email = sys.argv[2]

    token = crear_jwt(user_id, email)

    print("\n=== JWT GENERADO PARA EL LABORATORIO ===")
    print(token)
    print("\nUsuario representado:", email)
    print("ID:", user_id)
