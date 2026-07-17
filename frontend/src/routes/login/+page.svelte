<script>
  import { enhance } from '$app/forms';
  let { form } = $props();
  let busy = $state(false);
</script>

<div class="wrap">
  <div class="card">
    <div class="brand"><span class="mark" aria-hidden="true">S</span>ShopSphere</div>
    <h1>Welcome back</h1>
    <p class="muted">Log in to your account to continue.</p>

    <form
      method="POST"
      use:enhance={() => {
        busy = true;
        return async ({ update }) => {
          await update();
          busy = false;
        };
      }}
      class="login"
    >
      <label>
        <span>Username</span>
        <input name="username" value={form?.username ?? ''} autocomplete="username" required placeholder="testuser" />
      </label>
      <label>
        <span>Password</span>
        <input name="password" type="password" autocomplete="current-password" required placeholder="••••••••" />
      </label>
      {#if form?.error}<p class="err">{form.error}</p>{/if}
      <button type="submit" class="btn" disabled={busy}>{busy ? 'Signing in…' : 'Log in'}</button>
    </form>

    <div class="hint">
      <strong>Demo account</strong>
      <span><code>testuser</code> / <code>password</code></span>
    </div>
  </div>
</div>

<style>
  .wrap { display: grid; place-items: center; min-height: 60vh; padding: 1rem 0; }
  .card {
    width: 100%;
    max-width: 380px;
    background: var(--surface);
    border: 1.5px solid var(--border);
    border-radius: 26px 26px 26px 8px;   /* price-tag corner */
    padding: 2rem;
    box-shadow: var(--shadow);
  }
  .brand {
    display: flex; align-items: center; gap: 0.55rem;
    font-family: var(--disp); font-weight: 800; letter-spacing: -0.02em; margin-bottom: 1.25rem;
  }
  .brand .mark {
    display: grid; place-items: center;
    width: 26px; height: 26px;
    border-radius: 50% 50% 50% 6px;
    background: var(--teal); color: var(--surface);
    font-size: 0.95rem; transform: rotate(-8deg);
  }
  .card h1 { font-size: 1.5rem; margin-bottom: 0.3rem; }
  .login { display: flex; flex-direction: column; gap: 0.9rem; margin: 1.5rem 0 0; }
  label { display: flex; flex-direction: column; gap: 0.35rem; font-size: 0.85rem; font-weight: 500; color: var(--ink-soft); }
  input {
    padding: 0.7rem 0.85rem;
    border: 1.5px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--bg);
    color: var(--ink);
    font: inherit;
    outline: none;
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
  }
  input:focus { border-color: var(--teal); box-shadow: 0 0 0 4px var(--teal-soft); }
  .btn { padding: 0.75rem; margin-top: 0.25rem; }
  .btn:disabled { opacity: 0.65; cursor: default; }
  .err { color: var(--err); background: var(--err-soft); padding: 0.55rem 0.8rem; border-radius: var(--radius-sm); margin: 0; font-size: 0.88rem; }
  .hint {
    margin-top: 1.5rem;
    padding-top: 1.25rem;
    border-top: 1px solid var(--border);
    font-size: 0.85rem;
    color: var(--muted);
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
  }
  .hint code { background: var(--surface-2); padding: 0.1rem 0.4rem; border-radius: 6px; color: var(--ink-soft); }
</style>
