import { useEffect, useRef, useState } from "react";

const API = "http://localhost:8080/api";
const ZONAS = ["Nueva Córdoba", "Alta Córdoba", "Cerro de las Rosas", "Villa Allende", "Güemes"];

async function api(path, opts = {}, token) {
  // El Content-Type solo se manda cuando hay body: así los GET de búsqueda no disparan
  // un preflight CORS innecesario.
  const headers = { ...(token && { Authorization: "Bearer " + token }) };
  if (opts.body) headers["Content-Type"] = "application/json";
  const r = await fetch(API + path, { ...opts, headers });
  if (!r.ok) throw new Error((await r.json().catch(() => ({}))).error || "Error " + r.status);
  return r.json();
}

const EMPTY = { nombre: "", apellido: "", zona: "", descripcion: "", intereses: "", estudios: "" };

function ZonaCombo({ value, onChange, onPick, options }) {
  const [open, setOpen] = useState(false);
  const [idx, setIdx] = useState(-1);
  const ref = useRef(null);
  const shown = options.filter((o) => o.toLowerCase().includes(value.toLowerCase()));

  useEffect(() => {
    const close = (e) => { if (!ref.current?.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const pick = (o) => { onChange(o); onPick(o); setOpen(false); setIdx(-1); };
  const onKey = (e) => {
    if (e.key === "ArrowDown") { e.preventDefault(); setOpen(true); setIdx((i) => Math.min(i + 1, shown.length - 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setIdx((i) => Math.max(i - 1, 0)); }
    else if (e.key === "Enter" && open && idx >= 0) { e.preventDefault(); pick(shown[idx]); }
    else if (e.key === "Escape") setOpen(false);
  };

  return (
    <div className="combo" ref={ref}>
      <input
        role="combobox" aria-expanded={open} aria-controls="zonas-lista" autoComplete="off"
        placeholder="Buscar por zona de residencia" value={value}
        onChange={(e) => { onChange(e.target.value); setOpen(true); setIdx(-1); }}
        onFocus={() => setOpen(true)} onKeyDown={onKey}
      />
      {open && shown.length > 0 && (
        <ul id="zonas-lista" role="listbox">
          {shown.map((o, i) => (
            <li key={o} role="option" aria-selected={i === idx}
                className={i === idx ? "active" : ""} onClick={() => pick(o)}>{o}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function AuthBox({ onSession }) {
  const [mode, setMode] = useState("login");
  const [f, setF] = useState({ email: "", password: "" });
  const [err, setErr] = useState("");
  const submit = async (e) => {
    e.preventDefault();
    try { onSession(await api("/auth/" + mode, { method: "POST", body: JSON.stringify(f) })); }
    catch (ex) { setErr(ex.message); }
  };
  return (
    <form onSubmit={submit} className="panel">
      <h2>{mode === "login" ? "Ingresar" : "Crear cuenta"}</h2>
      <input placeholder="Email" type="email" required value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
      <input placeholder="Contraseña" type="password" required value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
      {err && <p className="err">{err}</p>}
      <button>{mode === "login" ? "Ingresar" : "Crear cuenta"}</button>
      <button type="button" className="link" onClick={() => setMode(mode === "login" ? "register" : "login")}>
        {mode === "login" ? "No tengo cuenta" : "Ya tengo cuenta"}
      </button>
    </form>
  );
}

function ProfileForm({ initial, onSave, onCancel }) {
  const [f, setF] = useState(initial);
  const fields = [["nombre", "Nombre"], ["apellido", "Apellido"], ["zona", "Zona de residencia"], ["estudios", "Estudios"], ["intereses", "Intereses personales"]];
  return (
    <form className="panel" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <h2>{f.id ? "Editar mi perfil" : "Registrar mi perfil"}</h2>
      {fields.map(([k, label]) => (
        <label key={k}>{label}<input required value={f[k] || ""} onChange={(e) => setF({ ...f, [k]: e.target.value })} /></label>
      ))}
      <label>Descripción<textarea rows="4" value={f.descripcion || ""} onChange={(e) => setF({ ...f, descripcion: e.target.value })} /></label>
      <button>Guardar perfil</button>
      {onCancel && <button type="button" className="link" onClick={onCancel}>Cancelar</button>}
    </form>
  );
}

export default function App() {
  const [session, setSession] = useState(() => JSON.parse(localStorage.getItem("session") || "null"));
  const [profiles, setProfiles] = useState([]);
  const [zona, setZona] = useState("");
  const [editing, setEditing] = useState(null);
  const [msg, setMsg] = useState("");
  const [searched, setSearched] = useState(false);

  const load = (z = zona) => {
    setSearched(z.trim() !== "");
    return api("/profiles" + (z ? "?zona=" + encodeURIComponent(z) : ""))
      .then((d) => { setProfiles(d); setMsg(""); })
      .catch((e) => { setMsg(e.message); setProfiles([]); });
  };
  useEffect(() => { load(""); }, []);

  const start = (s) => { localStorage.setItem("session", JSON.stringify(s)); setSession(s); };
  const logout = () => { localStorage.removeItem("session"); setSession(null); setEditing(null); };

  const save = async (p) => {
    try {
      await api(p.id ? "/profiles/" + p.id : "/profiles", { method: p.id ? "PUT" : "POST", body: JSON.stringify(p) }, session.token);
      setEditing(null); setMsg("Perfil guardado"); load();
    } catch (e) { setMsg(e.message); }
  };

  return (
    <main>
      <header>
        <h1>Cuidadores de mayores</h1>
        <p>Encontrá profesionales de cuidado por zona, o registrá tu perfil.</p>
        {session && <button className="link" onClick={logout}>Salir</button>}
      </header>

      <section className="cols">
        <div>
          <form className="search" onSubmit={(e) => { e.preventDefault(); load(); }}>
            <ZonaCombo value={zona} onChange={setZona} onPick={(z) => load(z)} options={ZONAS} />
            <button>Buscar</button>
          </form>
          {msg && <p className="msg">{msg}</p>}
          {profiles.length === 0 && !msg && (
            <p className="empty">
              {searched ? "No se encontraron perfiles para esa zona." : "Todavía no hay perfiles registrados."}
            </p>
          )}
          {profiles.map((p) => (
            <article key={p.id} className="card">
              <h3>{p.nombre} {p.apellido}</h3>
              <p className="zona">{p.zona}</p>
              <p>{p.descripcion}</p>
              <p><b>Estudios:</b> {p.estudios}</p>
              <p><b>Intereses:</b> {p.intereses}</p>
              {session && p.ownerId === session.userId && <button onClick={() => setEditing(p)}>Editar</button>}
            </article>
          ))}
        </div>
        <aside>
          {!session ? <AuthBox onSession={start} />
            : <ProfileForm key={editing?.id ?? "new"} initial={editing || EMPTY} onSave={save} onCancel={editing && (() => setEditing(null))} />}
        </aside>
      </section>
    </main>
  );
}
