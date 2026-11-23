# EFECTO TV — panel y servicio online

## Estado real
El repositorio contiene la interfaz del panel y las funciones servidoras. **No está operativo hasta conectar un proyecto Supabase y desplegarlo**. El APK actual todavía debe integrar el inicio de sesión con `customer-auth`; no entregar como versión final con control remoto hasta completar esa integración.

## 1. Backend
1. Crear un proyecto Supabase.
2. Ejecutar `supabase/schema.sql` completo en SQL Editor.
3. Crear un usuario administrador en Authentication → Users.
4. Copiar su UUID y ejecutar el INSERT comentado al final del SQL.
5. Desplegar las Edge Functions `admin-api` y `customer-auth`.
6. Configurar los secretos `SUPABASE_URL`, `SUPABASE_ANON_KEY` y `SUPABASE_SERVICE_ROLE_KEY`. La service role solo como secreto del servidor, nunca en GitHub, panel ni APK.

## 2. Panel
En `panel/index.html`, reemplazar `CONFIGURAR_SUPABASE_URL` y `CONFIGURAR_SUPABASE_ANON_KEY` por la URL y clave anon/public de Supabase. Las tablas usan RLS.

## 3. GitHub Pages
En GitHub → Settings → Pages, seleccionar **GitHub Actions** como fuente. El workflow `.github/workflows/deploy-panel.yml` publica la carpeta `panel`. URL esperada: `https://daniramirez0979-rgb.github.io/mi-iptv-efecto/`. Solo funciona tras habilitar Pages y finalizar correctamente el workflow.

## 4. API APK
POST a `/functions/v1/customer-auth` con JSON `{"username":"...","password":"..."}`. Respuesta correcta incluye `expires_at` y `playlist_url`. La APK debe validar al iniciar y periódicamente. Sin internet no puede recibir una baja en tiempo real.

## Privacidad de listas
La primera API entrega la URL a la app autenticada para reproducirla. **No es posible garantizar que sea imposible extraerla del dispositivo**. Para ocultarla de verdad hace falta un proxy de streaming del lado servidor, con costos de ancho de banda y compatibilidad por proveedor.

## Cuenta de prueba
No se creó todavía `damian1`: crearla desde el panel una vez desplegado el backend. Antes de configurar Supabase no hay credenciales activas.
