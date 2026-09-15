# Despliegue del entorno y primera prueba

Guía paso a paso para levantar **my-drive** en local (desarrollo) o en un servidor on-premise, y comprobar que el corte vertical funciona.

## Requisitos

| Entorno | Qué necesitas |
| --- | --- |
| **Servidor (recomendado para probar el stack completo)** | Docker Engine 24+ y Docker Compose v2 |
| **Desarrollo adicional** | Rust 1.85+, Node.js 22+ (ver `.nvmrc`), JDK 17+ y Android SDK para la app móvil |

Puertos que deben estar libres en la máquina donde levantes el compose de desarrollo:

- `80` — portal web (Nginx)
- `8080` — API (acceso directo y salud)
- `5432` — PostgreSQL (solo expuesto en desarrollo)
- `9000` / `9001` — MinIO (API y consola)

---

## 1. Preparar configuración

Desde la raíz del repositorio:

```bash
git clone https://github.com/jasensic/my-drive.git
cd my-drive
cp .env.example .env
```

Edita `.env` antes de usar esto en un servidor real:

1. Cambia `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD` y sobre todo `JWT_SECRET` (cadena larga y aleatoria).
2. En producción, ajusta `API_PUBLIC_URL` a la URL que usarán los clientes (por ejemplo `http://192.168.1.50` o tu dominio).

Los valores por defecto del ejemplo sirven **solo para pruebas locales**.

---

## 2. Levantar el stack (desarrollo / primera prueba)

```bash
docker compose -f deploy/docker-compose.yml up --build
```

La primera ejecución compila la API (Rust) y el portal (Angular); puede tardar varios minutos.

Cuando los contenedores estén en marcha:

| Servicio | URL |
| --- | --- |
| Portal web | http://localhost |
| API vía Nginx | http://localhost/v1 |
| API directa | http://localhost:8080/v1 |
| Salud | http://localhost:8080/health |
| OpenAPI (Swagger UI) | http://localhost:8080/api-docs |
| Consola MinIO | http://localhost:9001 (usuario/contraseña de `.env`) |

Para dejarlo en segundo plano:

```bash
docker compose -f deploy/docker-compose.yml up --build -d
docker compose -f deploy/docker-compose.yml logs -f api web
```

---

## 3. Comprobaciones rápidas (API)

Con el stack levantado:

```bash
curl -s http://localhost:8080/health | jq .
```

Respuesta esperada: JSON con `"status": "ok"`.

Setup del administrador (solo la **primera** vez, base de datos vacía):

```bash
curl -s -X POST http://localhost/v1/setup \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password123"}' | jq .
```

Guarda el campo `token` de la respuesta para las siguientes peticiones.

Ejemplo de subida (sustituye `TOKEN`):

```bash
curl -s -X POST http://localhost/v1/files \
  -H "Authorization: Bearer TOKEN" \
  -F "file=@/ruta/a/una/foto.jpg" | jq .
```

---

## 4. Primera prueba manual en el portal

1. Abre **http://localhost** (o http://localhost/login).
2. Si es la primera vez, verás **Create admin**:
   - Usuario: por ejemplo `admin`
   - Contraseña: por ejemplo `password123` (mínimo según validación del backend)
   - Pulsa **Complete setup**.
3. Tras el setup, inicia sesión con **Login** si hace falta.
4. En la biblioteca:
   - Pulsa **Upload** o usa el input de archivo.
   - Sube una imagen pequeña; debe aparecer el nombre del fichero en la lista.
5. Ve a **Devices**:
   - Nombre del dispositivo: por ejemplo `Device A`
   - **Register device**
   - **Edit rules** — debe mostrarse el diálogo **Sync rules**.

Si todos estos pasos funcionan, el flujo principal (auth → almacenamiento → dispositivos) está operativo.

---

## 5. Prueba automatizada (E2E del portal)

Con el stack en Docker (portal en el puerto **80**):

```bash
cd apps/web
npm ci
E2E_BASE_URL=http://localhost npx playwright install chromium
E2E_BASE_URL=http://localhost npm run test:e2e
```

El test `portal.spec.ts` reproduce setup/login, subida de `e2e/fixtures/tiny.jpg` y registro de un dispositivo.

Si desarrollas el portal con `npm start` (puerto **4200**), omite `E2E_BASE_URL` o usa `http://localhost:4200` y ten la API accesible según `apps/web/proxy.conf.json`.

---

## 6. Despliegue on-premise (NUC, NAS, Raspberry Pi)

Para un servidor que permanece encendido, con **mDNS** y actualizaciones automáticas de imágenes:

1. Configura `.env` con secretos de producción y `API_PUBLIC_URL` apuntando a la IP o hostname del servidor en la LAN.
2. Asegúrate de poder hacer pull de GHCR (o construye y etiqueta tus propias imágenes).
3. Arranca:

```bash
docker compose -f deploy/docker-compose.prod.yml up -d
```

Diferencias respecto al compose de desarrollo:

- La API y el portal usan `network_mode: host` para que el anuncio mDNS (`_mydrive._tcp.local`) funcione en Linux.
- **Watchtower** actualiza periódicamente las imágenes `api` y `web` sin borrar volúmenes de PostgreSQL ni MinIO.
- PostgreSQL no publica el puerto `5432` hacia fuera; la API se conecta por `127.0.0.1`.

Tras el despliegue, abre el portal desde otro equipo en la misma red usando la IP del servidor (por ejemplo `http://192.168.1.50`).

### mDNS y clientes Android

- En **Linux** con compose de producción, el servicio se anuncia en la LAN.
- En **macOS/Windows** con compose de desarrollo, mDNS suele estar desactivado en la API (`MDNS_ENABLE=false`); configura la URL del servidor manualmente en la app o usa la IP del host.
- La app Android descubre el servidor por NSD/mDNS cuando está en la misma Wi‑Fi; compílala con Android Studio o `./gradlew :app:assembleDebug` e instala el APK en un dispositivo de prueba.

---

## 7. Parar y limpiar

Solo parar contenedores (conserva datos en volúmenes):

```bash
docker compose -f deploy/docker-compose.yml down
# o
docker compose -f deploy/docker-compose.prod.yml down
```

Borrar también datos (PostgreSQL y MinIO):

```bash
docker compose -f deploy/docker-compose.yml down -v
```

---

## Problemas frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| Portal carga pero login falla | Logs de `api`: `docker compose -f deploy/docker-compose.yml logs api`. ¿PostgreSQL healthy? |
| `502` o error en `/v1` | API en marcha en `:8080`; en prod, Nginx debe alcanzar `127.0.0.1:8080`. |
| Setup devuelve error | El admin ya existe; usa **Login** en lugar de setup, o resetea volúmenes (`down -v`) en entorno de prueba. |
| Subida falla | Tamaño máximo 512 MB vía Nginx; credenciales MinIO en `.env` coherentes con el compose. |
| Android no encuentra el servidor | Misma subred Wi‑Fi, firewall del servidor, y en dev usar IP fija en lugar de mDNS. |

---

## Siguiente paso

Cuando el portal y la API respondan bien, prueba la sincronización desde Android con un perfil de sync restrictivo (por ejemplo solo fotos recientes) y comprueba que el manifiesto en `/v1/sync/manifest` lista los ficheros esperados.
