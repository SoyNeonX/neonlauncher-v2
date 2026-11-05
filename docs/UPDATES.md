# Auto-update desde GitHub Releases

El launcher se actualiza solo leyendo los **releases** del repositorio configurado en
`UpdateChecker.REPOSITORY` (`launcher-core/.../core/update/UpdateChecker.kt`).

```
REPOSITORY = "tuUsuario/NeonLauncher"
```

Si lo dejás vacío, el chequeo se saltea (útil mientras desarrollás).

---

## Cómo funciona

1. Al arrancar, el launcher pide
   `GET https://api.github.com/repos/<REPOSITORY>/releases/latest`.
2. Compara `tag_name` (sin la `v`) contra la versión compilada (`AppInfo.VERSION`).
3. Si hay una versión mayor, busca un asset `.exe` en la release y muestra
   **"NeonLauncher X.Y.Z ya está para bajar"** con el cuerpo del release como notas.
4. Si aceptás: baja el `.exe` a `%TEMP%`, verifica el `digest` SHA-256 que publica GitHub,
   lo ejecuta y cierra el launcher para que el instalador pueda reemplazar los archivos.

---

## Cómo publicar una versión

### 1. Subir la versión en el código

```kotlin
// launcher-common/.../common/util/AppInfo.kt
const val VERSION = "2.1.0"
```

### 2. Generar el instalador

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\tools\build-installer.ps1 -Version 2.1.0
```

Sale en `dist\installer\NeonLauncher-2.1.0.exe`. La primera vez el script baja WiX 3.11 a
`tools\wix311` (~40 MB, portable, no instala nada en el sistema).

### 3. Crear el release

- Tag: **`v2.1.0`** (con la `v`; el launcher la saca solo).
- Título: `NeonLauncher 2.1.0`.
- Descripción: las notas que va a leer el jugador (se muestran en el diálogo).
- Adjuntar: **`NeonLauncher-2.1.0.exe`**.

GitHub calcula el `digest` SHA-256 del asset automáticamente y lo expone en la API; el
launcher lo usa para verificar la descarga. No hace falta subir un `.sha256` aparte.

### 4. Probar

Con una versión anterior instalada, abrí el launcher: tiene que aparecer el aviso de
actualización. Si no aparece:

- revisá `REPOSITORY` (owner/repo exacto, sin `.git`);
- que el release **no** sea *draft* ni *pre-release* (`/latest` los ignora);
- que la versión del tag sea mayor que `AppInfo.VERSION`.

---

## Notas

- **Firma de código**: sin certificado, Windows SmartScreen va a mostrar "editor
  desconocido" al correr el instalador. Sirve igual, pero espanta. Ver la sección de firma
  en el README.
- **Actualización silenciosa**: hoy el instalador se abre con UI. Para que sea desatendido
  hay que compilar con Inno Setup o NSIS (soportan `/S`) en vez de jpackage/WiX.
- **Rollback**: el instalador de jpackage no desinstala la versión anterior; sobrescribe en
  el mismo `%LOCALAPPDATA%\NeonLauncher` (instalación por usuario).
- **Beta channel**: se puede extender `UpdateChecker` para leer
  `/releases` (lista) y elegir el primer *pre-release* cuando el usuario esté en canal beta.
