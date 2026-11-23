import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS"};
Deno.serve(async req=>{
 const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{...cors,"Content-Type":"application/json; charset=utf-8"}});
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 if(req.method!=="POST")return json({error:"Método no permitido"},405);
 try{
  const url=Deno.env.get("SUPABASE_URL"),key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if(!url||!key)return json({error:"Servidor sin configurar"},503);
  const body=await req.json(),username=String(body.username??"").trim(),password=String(body.password??"");
  if(!/^[a-zA-Z0-9_.-]{3,32}$/.test(username)||password.length<1||password.length>128)return json({error:"Usuario o contraseña incorrectos"},401);
  const db=createClient(url,key);
  const {data,error}=await db.rpc("verify_customer_login",{p_username:username,p_password:password});
  if(error){console.error("verify_customer_login failed",error.message);return json({error:"No se pudo validar el acceso"},503)}
  const customer=Array.isArray(data)?data[0]:data;
  if(!customer?.valid)return json({error:customer?.reason??"Usuario o contraseña incorrectos"},401);
  const playlists=[customer.playlist_url].filter(v=>typeof v==="string"&&/^https?:\/\//i.test(v));
  if(!playlists.length)return json({error:"El servicio todavía no tiene listas configuradas. Contactá al administrador."},503);
  return json({ok:true,username,expires_at:customer.expires_at,playlists,checked_at:new Date().toISOString()});
 }catch(error){console.error("customer-auth error",error);return json({error:"Solicitud inválida"},400)}
});
