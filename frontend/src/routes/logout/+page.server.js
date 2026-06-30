import { redirect } from '@sveltejs/kit';
import { SESSION_COOKIE } from '$lib/server/auth';

/** POST /logout clears the session cookie. */
export const actions = {
  default: async ({ cookies }) => {
    cookies.delete(SESSION_COOKIE, { path: '/' });
    throw redirect(303, '/');
  }
};
