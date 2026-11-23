create extension if not exists pgcrypto;
create table if not exists public.admin_users(user_id uuid primary key references auth.users(id) on delete cascade);
create table if not exists public.tv_customers(
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
-- Agregá tu UID de administrador después de crear tu cuenta Supabase:
-- insert into public.admin_users(user_id) values ('TU_AUTH_USER_UUID');
create or replace function public.set_customer_password(p_id uuid,p_password text)
returns void language plpgsql security definer set search_path=public,extensions as $$
begin
 if length(p_password)<6 then raise exception 'La contraseña debe tener al menos 6 caracteres'; end if;
 update public.tv_customers set password_hash=crypt(p_password,gen_salt('bf',12)) where id=p_id;
end; $$;
revoke all on function public.set_customer_password(uuid,text) from public,anon,authenticated;
grant execute on function public.set_customer_password(uuid,text) to service_role;
