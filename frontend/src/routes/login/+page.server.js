import { fail, redirect } from '@sveltejs/kit';
import { login, SESSION_COOKIE } from '$lib/server/auth';

/** Already logged in? Skip the form. */
export function load({ locals, url }) {
  if (locals.user) {
    throw redirect(303, url.searchParams.get('redirectTo') || '/');
  }
  return {};
}

export const actions = {
  default: async ({ request, fetch, cookies, url }) => {
    const form = await request.formData();
    const username = form.get('username');
    const password = form.get('password');
    if (!username || !password) {
      return fail(400, { error: 'Username and password are required.', username });
    }

    const tokens = await login(fetch, username, password);
    if (!tokens?.access_token) {
      return fail(401, { error: 'Invalid username or password.', username });
    }

    cookies.set(SESSION_COOKIE, tokens.access_token, {
      path: '/',
      httpOnly: true,
      sameSite: 'lax',
      secure: false, // dev runs over http; set true behind TLS / Cloudflare
      maxAge: tokens.expires_in ?? 300
    });

    throw redirect(303, url.searchParams.get('redirectTo') || '/cart');
  }
};
