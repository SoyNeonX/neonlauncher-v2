# NeonID — especificación del servicio de cuentas

Documento para que otro agente implemente el backend de **NeonID**, la cuenta propia de
NeonShy Studios. El launcher ya contempla el hueco en la UI (botón "NeonID · próximamente" en
el onboarding y en la barra lateral), pero no tiene cliente HTTP todavía.

---

## 1. Objetivo

Que un jugador pueda crear una cuenta que **no depende de Microsoft** y sirva para:

- identificarse en el launcher (`neon_id` + `password` o un token),
- entrar a los servidores de NeonShy Studios que acepten auth propia,
- recibir permisos especiales (`dev`, `staff`, `beta`, `support`) que el launcher lee para
  mostrar u ocultar funciones.

No reemplaza a Microsoft: conviven. Si el jugador tiene Microsoft, juega online en servidores
premium; si tiene NeonID, juega en los servidores del estudio con una identidad propia.

---

## 2. Modelo de datos

```jsonc
// Usuario
{
  "id": "nid_01HZX...",            // ULID/UUID, inmutable
  "username": "SoyNeonAR",          // 3-16, [A-Za-z0-9_], único (case-insensitive)
  "display_name": "SoyNeonAR",
  "uuid": "8c0a1f...",              // UUID v3 de "NeonID:<username>" (offline-style)
  "email": "opcional@mail.com",     // opcional, verificado o null
  "roles": ["player"],              // player | dev | staff | beta | support
  "created_at": "2026-11-01T18:00:00Z",
  "last_login_at": "2026-11-01T18:00:00Z",
  "disabled": false
}
```

El `uuid` se deriva **en el servidor** con la misma regla que usa Minecraft offline
(`UUID.nameUUIDFromBytes("NeonID:<username>")`) para que sea estable y reproducible; nunca se
genera al azar.

---

## 3. Endpoints (REST, JSON, HTTPS)

Base: `https://api.neonshy.studio/neonid/v1`

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `POST` | `/register` | `{username, password, email?}` | `201` `{user, access_token, refresh_token, expires_in}` |
| `POST` | `/login` | `{username, password}` | `200` mismo payload |
| `POST` | `/refresh` | `{refresh_token}` | `200` `{access_token, expires_in}` |
| `POST` | `/logout` | `{refresh_token}` | `204` |
| `GET` | `/me` | — (Bearer) | `200` `{user}` |
| `PATCH` | `/me` | `{display_name?, email?}` | `200` `{user}` |
| `POST` | `/me/password` | `{current_password, new_password}` | `204` |
| `GET` | `/.well-known/jwks.json` | — | claves públicas para validar JWT |
| `GET` | `/users/{id}` | — (público) | `{id, username, uuid, roles}` |

### Errores

```json
{ "error": "invalid_credentials", "message": "Usuario o contraseña incorrectos" }
```

Códigos: `400 validation_error`, `401 invalid_credentials`, `403 account_disabled`,
`409 username_taken`, `429 rate_limited`, `500 internal_error`.

Los mensajes van en español (el launcher los muestra tal cual).

---

## 4. Auth y tokens

- **Contraseñas**: Argon2id (o bcrypt cost 12+). Nunca SHA-256 solo.
- **Access token**: JWT firmado (RS256), 15 min, claims:
  `sub` (id), `username`, `uuid`, `roles`, `exp`, `iat`, `iss=neonid`.
- **Refresh token**: opaco, 30 días, rotativo (un uso), guardado hasheado en la DB.
- **Rate limit**: 10 intentos/min por IP en `/login` y `/register`.
- **Bloqueo**: 5 fallos seguidos → backoff exponencial por usuario.
- **Validación del username**: misma regex que el launcher (`^[A-Za-z0-9_]{3,16}$`), reservadas
  una lista de nombres (`admin`, `neon`, `neonshy`, `system`…).

---

## 5. Lo que necesita el launcher

1. `POST /login` con usuario y contraseña → guardar `access_token`, `refresh_token`, `uuid`,
   `username`, `roles` en `settings.json` (hoy el refresh de Microsoft va en claro; para NeonID
   conviene cifrar con DPAPI).
2. `GET /me` al arrancar para refrescar roles.
3. Si `roles` contiene `dev`, habilitar en el launcher: instancias de prueba, botón de
   publicar manifiesto y logs verbosos.
4. Si `roles` contiene `beta`, mostrar canales beta del servidor.
5. Al lanzar, el launcher manda `--accessToken <jwt>` y `--userType neonid` al servidor
   propio (el server plugin valida el JWT contra `/.well-known/jwks.json`).

---

## 6. Criterios de aceptación

- [ ] Registro y login funcionando con Argon2id y rate limit.
- [ ] JWT RS256 validable con JWKS público.
- [ ] Refresh rotativo con detección de reuso (si se reusa un refresh viejo, revocar la familia).
- [ ] `uuid` determinístico y documentado.
- [ ] Roles editables desde un panel de administración (aunque sea un JSON a mano al principio).
- [ ] Tests de integración de los 8 endpoints.
- [ ] OpenAPI 3 publicado en `/docs`.

---

## 7. Fuera de alcance (por ahora)

- Vincular NeonID con una cuenta de Microsoft.
- Recuperación de contraseña por email (fase 2).
- 2FA (fase 2, TOTP).
- Skins propias por NeonID (fase 3, requiere storage de texturas).

---

## 8. Orden sugerido de implementación

1. Esqueleto del servicio + `/register` + `/login` + `/me` (sin refresh).
2. Refresh rotativo + logout.
3. JWKS + validación en el plugin del servidor.
4. Panel mínimo de administración para asignar roles.
5. Rate limit, Argon2id y tests.
