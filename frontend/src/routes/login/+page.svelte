<script>
  import { enhance } from '$app/forms';
  let { form } = $props();
</script>

<h1>Log in</h1>
<p class="muted">Use your Keycloak credentials (dev seed: <code>testuser</code> / <code>password</code>).</p>

<form method="POST" use:enhance class="login">
  <label>
    Username
    <input name="username" value={form?.username ?? ''} autocomplete="username" required />
  </label>
  <label>
    Password
    <input name="password" type="password" autocomplete="current-password" required />
  </label>
  {#if form?.error}<p class="err">{form.error}</p>{/if}
  <button type="submit">Log in</button>
</form>

<style>
  .muted { color: #777; }
  .login { display: flex; flex-direction: column; gap: 1rem; max-width: 320px; margin-top: 1.5rem; }
  label { display: flex; flex-direction: column; gap: .35rem; font-size: .9rem; }
  input { padding: .5rem .6rem; border: 1px solid #ccc; border-radius: 6px; }
  button { padding: .6rem; border: 0; border-radius: 6px; background: #4f46e5; color: #fff; cursor: pointer; }
  .err { color: #b02a37; margin: 0; }
</style>
