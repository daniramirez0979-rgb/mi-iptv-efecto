create extension if not exists pgcrypto;

create table if not exists public.admin_users (
  user_id uuid primary key references auth.users(id) on delete cascade
);
create table if not exists public.tv_customers (
  id uuid primary key default gen_random_uuid(),
  username text unique not null,
  password_hash text not null,
  active boolean not null default true,
  expires_at timestamptz not null,
  playlist_url text,
  created_at timestamptz not null default now()
);
alter table public.admin_users enable row level security;
alter table public.tv_customers enable row level security;
revoke all on public.admin_users, public.tv_customers from anon, authenticated;

create or replace function public.set_customer_password(p_id uuid,p_password text)
returns void language plpgsql security definer set search_path=public,extensions as $$
begin
 if length(p_password)<6 or length(p_password)>128 then raise exception 'La contraseña debe tener entre 6 y 128 caracteres'; end if;
 update public.tv_customers set password_hash=crypt(p_password,gen_salt('bf',12)) where id=p_id;
 if not found then raise exception 'Usuario inexistente'; end if;
end; $$;
revoke all on function public.set_customer_password(uuid,text) from public,anon,authenticated;
grant execute on function public.set_customer_password(uuid,text) to service_role;

create or replace function public.verify_customer_login(p_username text,p_password text)
returns table(valid boolean,reason text,expires_at timestamptz,playlist_url text)
language plpgsql security definer set search_path=public,extensions as $$
declare c public.tv_customers%rowtype;
begin
 select * into c from public.tv_customers where username=p_username;
 if not found or c.password_hash='pending' or c.password_hash<>crypt(p_password,c.password_hash) then
  return query select false,'Usuario o contraseña incorrectos',null::timestamptz,null::text; return;
 end if;
 if not c.active then return query select false,'Cuenta desactivada. Contactá al administrador.',c.expires_at,null::text; return; end if;
 if c.expires_at<=now() then return query select false,'Tu cuenta venció. Contactá al administrador.',c.expires_at,null::text; return; end if;
 return query select true,null::text,c.expires_at,c.playlist_url;
end; $$;
revoke all on function public.verify_customer_login(text,text) from public,anon,authenticated;
grant execute on function public.verify_customer_login(text,text) to service_role;

-- Tras crear el administrador en Supabase Auth, insertar su UUID:
-- insert into public.admin_users(user_id) values ('UUID_DEL_ADMIN');
