# Fatum FileService — almacenamiento compartido

Microservicio de archivos para todos los servicios de Fatum. Resuelve un problema concreto: **cada
microservicio subía archivos a S3 por su cuenta**, repitiendo credenciales, políticas de bucket y
validaciones, y obligando a desplegarlos todos cuando cambiaba una regla.

Aquí el cliente no elige bucket: elige una **ruta** (`microservicio:propósito`) y el servicio decide
dónde va el objeto, con qué prefijo, qué tipos acepta y cuánto puede pesar.

```
"user-service:profile-image"  →  bucket fatum-user-profile-…  prefijo profile-images
"user-service:liveness"       →  bucket configurable          prefijo liveness
"user-service:document"       →  bucket fatum-documents-…     prefijo documents
"ratings-service:photo"       →  bucket fatum-ratings-…       prefijo photos
"catalog-service:photo"       →  bucket fatum-catalog-…       prefijo photos
```

Añadir un bucket para otro microservicio es **configuración, no código**: la misma imagen corre en todos
los entornos y el nombre del bucket entra por variable de entorno.

## Arquitectura

| Paquete | Responsabilidad |
| --- | --- |
| `fatum.filestorage.configuration` | Propiedades de rutas, cliente S3 y filtro de secreto interno. |
| `fatum.filestorage.service` | Resolución de rutas, construcción de claves y acceso a S3. |
| `fatum.filestorage.controller` | API HTTP y traducción de errores. |
| `fatum.filestorage.dto` | Contratos de entrada/salida. |
| `fatum.filestorage.exception` | Catálogo de errores (`StorageError`). |

## API

| Método | Ruta | Resultado |
| --- | --- | --- |
| `POST` | `/files?route=…` | sube un archivo (`multipart/form-data`, campo `file`) |
| `GET` | `/files/url?route=…&key=…` | URL prefirmada de descarga |
| `DELETE` | `/files?route=…&key=…` | borra el objeto (idempotente) |
| `GET` | `/files/routes` | catálogo de rutas configuradas |
| `GET` | `/actuator/health` | estado |

Todas las llamadas llevan la cabecera `X-Storage-Key` con el secreto interno. Si
`STORAGE_INTERNAL_SECRET` está vacío, la comprobación se desactiva (solo para desarrollo local).

### Ejemplo

```bash
curl -X POST "http://localhost:8081/files?route=user-service:document" \
  -H "X-Storage-Key: $STORAGE_INTERNAL_SECRET" \
  -F "file=@frente.jpg;type=image/jpeg"
```

```json
{
  "id": "obj-1",
  "key": "documents/2026/10/01/8f1c…-frente.jpg",
  "bucket": "fatum-documents-902353451847-us-east-1-an",
  "route": "user-service:document",
  "originalFilename": "frente.jpg",
  "contentType": "image/jpeg",
  "size": 145233,
  "etag": "\"9c1f…\""
}
```

## Rutas

Cada ruta declara bucket, prefijo, tipos MIME permitidos, tamaño máximo y vigencia de las URLs
prefirmadas:

```yaml
fatum:
  storage:
    routes:
      "user-service:document":
        bucket: ${S3_USER_DOCUMENTS_BUCKET:}
        prefix: documents
        allowed-content-types: [image/jpeg, image/png, image/webp]
        max-size: ${S3_USER_DOCUMENTS_MAX_SIZE:10MB}
```

Las claves se construyen como `prefijo/aaaa/MM/dd/uuid-nombre-seguro`: la fecha reparte los objetos y
facilita reglas de ciclo de vida, el uuid evita colisiones y el nombre se sanea antes de usarlo.

## Variables de entorno

| Variable | Descripción | Ejemplo |
| --- | --- | --- |
| `PORT` | Puerto HTTP. | `8081` |
| `STORAGE_INTERNAL_SECRET` | Secreto esperado en `X-Storage-Key`. | `…` |
| `STORAGE_PRESIGN_TTL` | Vigencia por defecto de las URLs. | `15m` |
| `STORAGE_DEFAULT_ROUTE` | Ruta cuando el cliente no envía ninguna. | *(vacío)* |
| `AWS_REGION` | Región de los buckets. | `us-east-1` |
| `S3_USER_PROFILE_IMAGES_BUCKET` | Bucket de fotos de perfil. | `fatum-user-profile-…` |
| `S3_USER_LIVENESS_BUCKET` | Bucket de evidencia de vida. | *(por defecto, el de perfil)* |
| `S3_USER_DOCUMENTS_BUCKET` | Bucket de documentos de identidad. | `fatum-documents-…` |
| `S3_RATINGS_PHOTOS_BUCKET` | Bucket del servicio de calificaciones. | `fatum-ratings-…` |
| `S3_CATALOG_PHOTOS_BUCKET` | Bucket del servicio de catálogo. | `fatum-catalog-…` |
| `STORAGE_MAX_FILE_SIZE` | Límite duro del contenedor. | `15MB` |
| `STORAGE_MAX_REQUEST_SIZE` | Límite duro de la petición. | `15MB` |

Cada ruta puede además sobrescribir su tamaño máximo con `S3_<RUTA>_MAX_SIZE` (por ejemplo
`S3_USER_DOCUMENTS_MAX_SIZE`). El límite de la ruta es siempre menor o igual que el del contenedor.

## Compilación

```bash
mvn clean verify
```

## Permisos IAM necesarios

```json
{
  "Effect": "Allow",
  "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
  "Resource": [
    "arn:aws:s3:::fatum-user-profile-*/profile-images/*",
    "arn:aws:s3:::fatum-user-profile-*/liveness/*",
    "arn:aws:s3:::fatum-documents-*/documents/*"
  ]
}
```
