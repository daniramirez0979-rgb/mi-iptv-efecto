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
  const validUrl=(v)=>!v||(typeof v==="string"&&v.length<=4096&&/^https?:\/\//i.test(v));
  if(a==="upload_ad_image"){
   const encoded=String(b.image_base64||""),mime=String(b.mime_type||"");
   if(!["image/jpeg","image/png","image/webp"].includes(mime)||!encoded||encoded.length>7000000)return json({error:"Imagen inválida o demasiado grande. Elegí una foto de hasta 5 MB."},400);
   const bytes=Uint8Array.from(atob(encoded),c=>c.charCodeAt(0));
   const ext=mime==="image/png"?"png":mime==="image/webp"?"webp":"jpg";
   const {error}=await db.storage.from("tv-advertisements").upload("current."+ext,bytes,{contentType:mime,upsert:true,cacheControl:"60"});
   if(error)throw error;
   const {data}=db.storage.from("tv-advertisements").getPublicUrl("current."+ext);
   return json({ok:true,image_url:data.publicUrl});
  }
  if(a==="remove_ad"){
   const {error}=await db.from("tv_ad_settings").upsert({id:1,enabled:false,image_url:null,updated_at:new Date().toISOString()},{onConflict:"id"});
   if(error)throw error;
   const {error:removeError}=await db.storage.from("tv-advertisements").remove(["current.jpg","current.png","current.webp"]);
   if(removeError)throw removeError;
   return json({ok:true});
  }
  if(a==="get_ad"){
   const {data,error}=await db.from("tv_ad_settings").select("enabled,image_url,initial_delay_minutes,interval_minutes,duration_seconds,updated_at").eq("id",1).maybeSingle();
   if(error)throw error;return json(data||{enabled:false,image_url:"",initial_delay_minutes:3,interval_minutes:30,duration_seconds:8});
  }
  if(a==="set_ad"){
   const image=String(b.image_url||"").trim(),initial=Number(b.initial_delay_minutes),interval=Number(b.interval_minutes),duration=Number(b.duration_seconds);
   if(image&&!/^https:\/\//i.test(image))return json({error:"La imagen debe tener una URL HTTPS."},400);
   if(!Number.isInteger(initial)||initial<1||initial>1440||!Number.isInteger(interval)||interval<5||interval>1440||!Number.isInteger(duration)||duration<3||duration>30)return json({error:"Tiempos inválidos."},400);
   if(b.enabled&&!image)return json({error:"Ingresá el enlace de la imagen antes de activar la publicidad."},400);
   const {error}=await db.from("tv_ad_settings").upsert({id:1,enabled:!!b.enabled,image_url:image||null,initial_delay_minutes:initial,interval_minutes:interval,duration_seconds:duration,updated_at:new Date().toISOString()},{onConflict:"id"});
   if(error)throw error;return json({ok:true});
  }
  if(a==="create"){
   const username=String(b.username||"").trim(),password=String(b.password||""),days=Number(b.days);
   if(!/^[a-zA-Z0-9_.-]{3,32}$/.test(username)||password.length<6||!Number.isInteger(days)||days<1||days>3650)return json({error:"Datos inválidos. Usuario 3-32 caracteres, contraseña mínimo 6 y días 1-3650."},400);
   const {data,error}=await db.from("tv_customers").insert({username,password_hash:"pending",expires_at:new Date(Date.now()+days*86400000).toISOString()}).select("id").single();
   if(error)return json({error:error.message},400);
   const {error:pe}=await db.rpc("set_customer_password",{p_id:data.id,p_password:password});
   if(pe){await db.from("tv_customers").delete().eq("id",data.id);return json({error:pe.message},400)}
   return json({ok:true});
  }
  if(a==="global_lists"){
   const {data,error}=await db.from("tv_playlist_settings").select("primary_url,secondary_url,updated_at").eq("id",1).maybeSingle();
   if(error)throw error;return json({primary_url:data?.primary_url||"",secondary_url:data?.secondary_url||"",updated_at:data?.updated_at||null});
  }
  if(a==="set_global_lists"){
   const primary=String(b.primary_url||"").trim();
   if(!validUrl(primary))return json({error:"Las listas deben ser URL http:// o https:// válidas."},400);
   if(!primary&&!secondary)return json({error:"Ingresá al menos una lista."},400);
   const {error}=await db.from("tv_playlist_settings").upsert({id:1,primary_url:primary||null,secondary_url:null,updated_at:new Date().toISOString()},{onConflict:"id"});
   if(error)throw error;return json({ok:true});
  }
  if(a==="list"){const {data,error}=await db.from("tv_customers").select("id,username,active,expires_at,created_at").order("created_at",{ascending:false});if(error)throw error;return json({users:data})}
  if(a==="status"){const {error}=await db.from("tv_customers").update({active:!!b.active}).eq("id",b.id);if(error)throw error;return json({ok:true})}
  if(a==="delete"){const {error}=await db.from("tv_customers").delete().eq("id",b.id);if(error)throw error;return json({ok:true})}
  return json({error:"Acción desconocida"},400);
 }catch(e){return json({error:e.message||"Error interno"},500)}
});
