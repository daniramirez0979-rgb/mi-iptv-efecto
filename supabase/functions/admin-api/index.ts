import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type"};
Deno.serve(async req=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 const json=(x,s=200)=>new Response(JSON.stringify(x),{status:s,headers:{...cors,"Content-Type":"application/json"}});
 try{
  const url=Deno.env.get("SUPABASE_URL")!, anon=Deno.env.get("SUPABASE_ANON_KEY")!, key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const auth=req.headers.get("Authorization")||"";
  const userClient=createClient(url,anon,{global:{headers:{Authorization:auth}}});
  const {data:{user},error:ue}=await userClient.auth.getUser();if(ue||!user)return json({error:"No autorizado"},401);
  const db=createClient(url,key);
  const {data:admin}=await db.from("admin_users").select("user_id").eq("user_id",user.id).maybeSingle();
  if(!admin)return json({error:"Sin permisos de administrador"},403);
  const b=await req.json(),a=b.action;
  if(a==="create"){
   const username=String(b.username||"").trim(),password=String(b.password||""),days=Number(b.days);
   if(!/^[a-zA-Z0-9_.-]{3,32}$/.test(username)||password.length<6||!Number.isInteger(days)||days<1||days>3650)return json({error:"Datos inválidos. Usuario 3-32 caracteres, contraseña mínimo 6 y días 1-3650."},400);
   const {data,error}=await db.from("tv_customers").insert({username,password_hash:"pending",expires_at:new Date(Date.now()+days*86400000).toISOString(),playlist_url:b.playlist_url||null}).select("id").single();
   if(error)return json({error:error.message},400);
   const {error:pe}=await db.rpc("set_customer_password",{p_id:data.id,p_password:password});
   if(pe){await db.from("tv_customers").delete().eq("id",data.id);return json({error:pe.message},400)}
   return json({ok:true});
  }
  if(a==="list"){const {data,error}=await db.from("tv_customers").select("id,username,active,expires_at,playlist_url,created_at").order("created_at",{ascending:false});if(error)throw error;return json({users:data})}
  if(a==="status"){const {error}=await db.from("tv_customers").update({active:!!b.active}).eq("id",b.id);if(error)throw error;return json({ok:true})}
  if(a==="playlist"){const {error}=await db.from("tv_customers").update({playlist_url:b.playlist_url||null}).eq("id",b.id);if(error)throw error;return json({ok:true})}
  if(a==="delete"){const {error}=await db.from("tv_customers").delete().eq("id",b.id);if(error)throw error;return json({ok:true})}
  return json({error:"Acción desconocida"},400);
 }catch(e){return json({error:e.message||"Error interno"},500)}
});