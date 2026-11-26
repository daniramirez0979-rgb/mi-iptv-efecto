# EFECTO TV — panel y servicio online

## Estado
El repositorio ya contiene el esquema de listas globales (principal y respaldo), el panel para administrarlas y la validación individual de usuarios. **Todavía no está operativo** hasta conectar Supabase y desplegar las funciones. La APK aún debe integrar customer-auth y consumir ambas listas globales.

## 1. Backend Supabase
1. Crear un proyecto Supabase.
2. Ejecutar `supabase/schema.sql` completo en SQL Editor.
3. Crear un usuario administrador en Authentication → Users.
4. Copiar su UUID y ejecutar el INSERT comentado al final del SQL.
5. Desplegar las Edge Functions `admin-api` y `customer-auth`.
6. Configurar `SUPABASE_URL`, `SUPABASE_ANON_KEY` y `SUPABASE_SERVICE_ROLE_KEY` como secretos del servidor. La service role nunca debe ponerse en la APK, panel ni repositorio.

## 2. Panel
En `panel/index.html`, reemplazar `CONFIGURAR_SUPABASE_URL` y `CONFIGURAR_SUPABASE_ANON_KEY` por la URL y clave anon/public de Supabase. En GitHub → Settings → Pages, seleccionar GitHub Actions como fuente. URL esperada: `https://daniramirez0979-rgb.github.io/mi-iptv-efecto/`.

El panel tiene un bloque **Listas generales para TODOS los clientes** con URL principal y secundaria opcional. Los usuarios individuales solo tienen usuario, contraseña, vencimiento y estado; no llevan una lista propia.

## 3. Validación de clientes
POST a `/functions/v1/customer-auth` con JSON `{"username":"...","password":"..."}`. La respuesta correcta incluye `expires_at` y `playlists`, un array con la lista principal y, si existe, la secundaria. La APK debe intentar la secundaria cuando no pueda cargar la principal y volver a consultar el servidor para recibir cambios y bajas.

## Privacidad
La app necesita recibir la URL para reproducirla, por lo que no se puede garantizar que sea imposible extraerla del dispositivo. Para ocultarla realmente hace falta un proxy de streaming del lado servidor, con costos de ancho de banda y compatibilidad por proveedor.

## Cuenta de prueba
No se creó todavía `damian1`. Se podrá crear desde el panel una vez conectado y desplegado Supabase.


## Publicidad remota en TV Efecto
La función requiere la migración de `tv_ad_settings` incluida al final de `supabase/schema.sql`, y desplegar la Edge Function `tv-advertisement`. El panel permite configurar una URL HTTPS pública de imagen horizontal, espera inicial, intervalo y duración. La APK consulta esa configuración y solo muestra la imagen cuando el usuario está en Inicio; nunca sobre canales ni películas. Valores iniciales: 3 minutos, cada 30 minutos, 8 segundos. La primera actualización de APK incorpora el lector remoto; las siguientes imágenes y tiempos se cambian desde el panel.
