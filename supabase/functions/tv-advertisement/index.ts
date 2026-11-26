import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const cors={"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"GET, OPTIONS"};
Deno.serve(async req=>{
 if(req.method==="OPTIONS")return new Response("ok",{headers:cors});
 if(req.method!=="GET")return new Response(JSON.stringify({error:"Método no permitido"}),{status:405,headers:{...cors,"Content-Type":"application/json"}});
 try{
  const url=Deno.env.get("SUPABASE_URL"),key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");if(!url||!key)throw new Error("Servidor sin configurar");
  const db=createClient(url,key);const {data,error}=await db.from("tv_ad_settings").select("enabled,image_url,initial_delay_minutes,interval_minutes,duration_seconds,updated_at").eq("id",1).maybeSingle();if(error)throw error;
  const valid=!!data?.enabled&&typeof data.image_url==="string"&&data.image_url.startsWith("https://");
  return new Response(JSON.stringify({enabled:valid,image_url:valid?data.image_url:"",initial_delay_minutes:data?.initial_delay_minutes??3,interval_minutes:data?.interval_minutes??30,duration_seconds:data?.duration_seconds??8,updated_at:data?.updated_at??null}),{headers:{...cors,"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}});
 }catch(e){return new Response(JSON.stringify({enabled:false,error:e.message||"Error interno"}),{status:503,headers:{...cors,"Content-Type":"application/json"}})}
});
